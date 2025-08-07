/**
 * Copyright 2017 Joakim von Kistowski
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package tools.descartes.dlim.httploadgenerator.http;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Logger;

import java.net.UnknownHostException;
import java.net.NoRouteToHostException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.nio.channels.UnresolvedAddressException;
import java.util.concurrent.RejectedExecutionException;

import org.eclipse.jetty.client.BufferingResponseListener;
import org.eclipse.jetty.client.Request;
import org.eclipse.jetty.client.Response;
import org.eclipse.jetty.client.Result;
import org.eclipse.jetty.http.HttpField;
import org.eclipse.jetty.http.HttpHeader;

import tools.descartes.dlim.httploadgenerator.generator.ResultTracker;
import tools.descartes.dlim.httploadgenerator.generator.ResultTracker.TransactionState;
import tools.descartes.dlim.httploadgenerator.transaction.Transaction;
import tools.descartes.dlim.httploadgenerator.transaction.TransactionQueueSingleton;


/**
 * {@link HTTPTransaction} sends HTML requests to a HTTP web server based on a LUA
 * script.
 *
 * @author Joakim von Kistowski, Maximilian Deffner
 *
 */
public class HTTPTransaction extends Transaction {

	private static final String POST_SIGNAL = "[POST]";
	private static final String PUT_SIGNAL = "[PUT]";

	/** The constant logging instance. */
	private static final Logger LOG = Logger.getLogger(HTTPTransaction.class.getName());

	/**
     * Asynchronously processes an HTTP transaction using Jetty's non-blocking client.
     * This method is more scalable and recommended for high-throughput load generation.
     *
     * @param generator The {@link HTTPInputGenerator} that provides the next target URL and request method.
     */
	private void processAsync(HTTPInputGenerator generator) {
		long processStartTime = System.currentTimeMillis();
		long requestStartTime = System.nanoTime();
		int requestNum = generator.getCurrentCallNum();

		// Check if request is dropped. This indicates a bottleneck in the loadgenerator not in the application.
		if (generator.getTimeout() > 0 && processStartTime - getStartTime() > generator.getTimeout()) {
			LOG.warning("Wait time in queue too long. "
				+ String.valueOf(processStartTime - getStartTime())
				+ " ms passed before transaction was even started.");
			logResultAndReleaseResources(
				new HTTPTransactionResult(
					this.getTargetTime(),
					ResultTracker.TransactionState.DROPPED,
					requestNum
				),
				generator
			);
			return;
		}

		String url = generator.getNextInput().trim();
		String method = "GET";
		if (url.startsWith("[")) {
			if (url.startsWith(POST_SIGNAL)) {
				method = "POST";
			}
			if (url.startsWith(PUT_SIGNAL)) {
				method = "PUT";
			}
			url = url.replaceFirst("\\[.*?\\]", "");
		}

		Request request = generator.initializeHTTPRequest(url, method);
		HTTPTransactionResult httpResult = new HTTPTransactionResult(
				this.getTargetTime(),
				ResultTracker.TransactionState.SUCCESS,
				requestNum
		);
		httpResult.setMethod(method);
		int index = url.indexOf("[");
		httpResult.setRequestURI(index != -1 ? url.substring(0, index) : url);

		try {
			ResultTracker.TRACKER.addSentRequest();
			request.send(new BufferingResponseListener() {
				@Override
				public void onComplete(Result result) {
					try {
						long responseTime = calculateResponseTime(requestStartTime);
						httpResult.setResponseTime(responseTime);

						//Check for exception
						if (result.isFailed()) {
							generator.revertLastCall();
							httpResult.setTransactionState(TransactionState.FAILED);

							Throwable e = result.getFailure();

							if (e instanceof TimeoutException) {
								httpResult.setTransactionState(TransactionState.TIMEOUT);
								// Overwrite response time to be timeout
								httpResult.setResponseTime(generator.getTimeout());
								LOG.finest("TimeoutException: " + e.getMessage());
								logResultAndReleaseResources(httpResult, generator);
								return;
							}

							if (e instanceof ExecutionException) {
								Throwable cause = e.getCause();
								if (e instanceof SocketTimeoutException && isConnectTimeout(e)) {
									httpResult.setTransactionState(TransactionState.TIMEOUT);
									logResultAndReleaseResources(httpResult, generator);
									return;
								}
								if (isNotSentException(cause)) {
									LOG.severe("ExecutionException before sending the request: " + cause.getMessage());
									httpResult.setTransactionState(TransactionState.DROPPED);
								}
								logResultAndReleaseResources(httpResult, generator);
								return;
							}

							if (isNotSentException(e)) {
								LOG.severe("Not sent exception: " + e.getClass().getCanonicalName() + ": " + e.getMessage());
								httpResult.setTransactionState(TransactionState.DROPPED);
								logResultAndReleaseResources(httpResult, generator);
								return;
							}

							if (e instanceof SocketTimeoutException && isConnectTimeout(e)) {
								httpResult.setTransactionState(TransactionState.TIMEOUT);
								logResultAndReleaseResources(httpResult, generator);
								return;
							}

							LOG.finest(e.getClass().getCanonicalName() + ": " + e.getMessage());
							logResultAndReleaseResources(httpResult, generator);
							return;
						}


						Response response = result.getResponse();

						for (HttpField field : response.getHeaders().getFields(HttpHeader.SET_COOKIE)) {
							generator.addCookie(request.getURI(), field);
						}

						// Handle 4XX and 5XX status codes
						if (response.getStatus() >= 400) {
							generator.revertLastCall();
							LOG.finest("Received error response code: " + response.getStatus());
							httpResult.setTransactionState(TransactionState.FAILED);

							logResultAndReleaseResources(httpResult, generator);
							return;
						}
						try {
							String content = this.getContentAsString();
							generator.resetHTMLFunctions(content);
						} catch (Exception e) {
							LOG.warning("Failed to parse response body: " + e.getMessage());
						}

						logResultAndReleaseResources(httpResult, generator);
						return;
					} catch (Throwable t) {
						LOG.severe("Uncaught exception in onComplete: " + t.getClass().getSimpleName() + " - " + t.getMessage());
						httpResult.setTransactionState(TransactionState.FAILED);
						logResultAndReleaseResources(httpResult, generator);

					}
				}
			});
		} catch (Exception e) {
			LOG.severe("Request.send() failed before listener could be attached: " + e.getMessage());
			httpResult.setTransactionState(TransactionState.DROPPED);
			logResultAndReleaseResources(httpResult, generator);
		}
	}

	/**
     * Calculates the response time in milliseconds based on a given start time in nanoseconds.
     * This method uses the current system time to compute the duration since the start.
     *
     * @param startNanos The start time in nanoseconds, typically captured with {@code System.nanoTime()}.
     * @return The elapsed time in milliseconds since {@code startNanos}.
     */
	private long calculateResponseTime(long startNanos) {
		return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
	}

	/**
     * Checks if the provided exception represents a client-side error
     * that occurred before the HTTP request was sent.
     *
     * @param cause The exception cause to check.
     * @return true if this is a client-side error; false otherwise.
	 */
	private static boolean isNotSentException(Throwable cause) {
        return cause instanceof UnknownHostException ||
               cause instanceof NoRouteToHostException ||
               cause instanceof ConnectException ||
               cause instanceof UnresolvedAddressException ||
               cause instanceof RejectedExecutionException ||
               cause instanceof IllegalArgumentException ||
               cause instanceof IllegalStateException ||
               cause instanceof java.security.GeneralSecurityException ||
               cause instanceof org.eclipse.jetty.client.HttpRequestException;
    }

	/**
     * Determines whether the given {@link Throwable} represents a connection timeout
     * based on its message content. This is typically used to distinguish between
     * different types of {@link SocketTimeoutException}.
     *
     * @param t The {@link Throwable} to inspect.
     * @return {@code true} if the exception message indicates a connection timeout; {@code false} otherwise.
     */
	private static boolean isConnectTimeout(Throwable t) {
        return t.getMessage() != null && t.getMessage().toLowerCase().contains("connect");
    }

	/**
     * Logs the transaction result, releases the input generator back to the pool,
     * and requeues this transaction for future reuse.
     *
     * @param result The transaction result to log.
     * @param generator The input generator used for this transaction.
     */
	private void logResultAndReleaseResources(HTTPTransactionResult result, HTTPInputGenerator generator) {
		ResultTracker.TRACKER.logTransaction(result);
		HTTPInputGeneratorPool.getPool().releaseBackToPool(generator);
		TransactionQueueSingleton transactionQueue = TransactionQueueSingleton.getInstance();
		transactionQueue.addQueueElement(this);
	}

	@Override
	public void run() {
		try {
		HTTPInputGenerator generator = HTTPInputGeneratorPool.getPool().takeFromPool();
		processAsync(generator);
		} catch (Exception e) {
			LOG.severe("Unexpected error in HTTPTransaction.run: " +
				e.getClass().getCanonicalName() + ": " + e.getMessage());
		}
	}

	/**
	 * Represents the result of an HTTP transaction including timing, status,
 	 * and request metadata.
 	 */
	public class HTTPTransactionResult {
		private long responseTime = 0;

		private String requestURI = "";

		private String method = "";

		private double transactionTargetStartTime;

		private ResultTracker.TransactionState transactionState;

		private int requestNum;



		public HTTPTransactionResult(double transactionTargetStartTime, ResultTracker.TransactionState transactionState, int requestNum) {
			this.transactionTargetStartTime = transactionTargetStartTime;
			this.transactionState = transactionState;
			this.requestNum = requestNum;
		}

		public int getRequestNum() {
			return requestNum;
		}

		public double getTransactionTargetStartTime() {
			return transactionTargetStartTime;
		}

		public ResultTracker.TransactionState getTransactionState() {
			return transactionState;
		}

		public void setTransactionState(ResultTracker.TransactionState transactionState) {
			this.transactionState = transactionState;
		}

		public long getResponseTime() {
			return responseTime;
		}

		public void setResponseTime(long responseTime) {
			this.responseTime = responseTime;
		}

		public String getRequestURI() {
			return requestURI;
		}

		public void setRequestURI(String requestURI) {
			this.requestURI = requestURI;
		}

		public String getMethod() {
			return method;
		}

		public void setMethod(String method) {
			this.method = method;
		}
	}
}
