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

import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.jetty.client.ContentResponse;
import org.eclipse.jetty.client.Request;

import tools.descartes.dlim.httploadgenerator.generator.ResultTracker;
import tools.descartes.dlim.httploadgenerator.generator.ResultTracker.TransactionState;
import tools.descartes.dlim.httploadgenerator.transaction.Transaction;
import tools.descartes.dlim.httploadgenerator.transaction.TransactionQueueSingleton;

/**
 * HTTP transaction sends HTML requests to a HTTP web server based on a LUA
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
	 * Processes the transaction of sending a GET request to a web server.
	 *
	 * @param generator The input generator to use.
	 * @return Response time in milliseconds.
	 */
	public HTTPTransactionResult process(HTTPInputGenerator generator) {
		long processStartTime = System.currentTimeMillis();
		long performanceStartTime = System.nanoTime();
		int requestNum = generator.getCurrentCallNum();
		if (generator.getTimeout() > 0 && processStartTime - getStartTime() > generator.getTimeout()) {
			LOG.warning("Wait time in queue too long. "
					+ String.valueOf(processStartTime - getStartTime())
					+ " ms passed before transaction was even started.");
			return new HTTPTransactionResult(this.getTargetTime(), ResultTracker.TransactionState.DROPPED, requestNum);
		}

		String url = generator.getNextInput().trim();
		requestNum = generator.getCurrentCallNum();
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

		HTTPTransactionResult result = new HTTPTransactionResult(this.getTargetTime(), ResultTracker.TransactionState.SUCCESS, requestNum);
		result.setMethod(method);
		result.setRequestURI(url);

		try {
			ContentResponse response = request.send();
			if (response.getStatus() >= 400) {
				long responseTime = System.nanoTime() - performanceStartTime;
			responseTime = TimeUnit.NANOSECONDS.toMillis(responseTime);
				generator.revertLastCall();
				LOG.log(Level.FINEST, "Received error response code: " + response.getStatus());
				result.setTransactionState(TransactionState.FAILED);
				result.setResponseTime(responseTime);
			} else {
				String responseBody = response.getContentAsString();
				long responseTime = System.nanoTime() - performanceStartTime;
			responseTime = TimeUnit.NANOSECONDS.toMillis(responseTime);

				// store result
				generator.resetHTMLFunctions(responseBody);
				result.setResponseTime(responseTime);
			}
		} catch (TimeoutException e) {
			generator.revertLastCall();
			result.setResponseTime(generator.getTimeout());
			result.setTransactionState(TransactionState.TIMEOUT);
			LOG.warning("TimeoutException: " + e.getMessage());
		} catch (ExecutionException e) {
			long responseTime = System.nanoTime() - performanceStartTime;
			responseTime = TimeUnit.NANOSECONDS.toMillis(responseTime);
			if (e.getCause() == null || !(e.getCause() instanceof TimeoutException)) {
				LOG.log(Level.SEVERE,
						"ExecutionException in call for URL: " + url + "; Cause: " + e.getCause().toString());
			}
			generator.revertLastCall();
			result.setTransactionState(TransactionState.FAILED);
			result.setResponseTime(responseTime);
		} catch (CancellationException e) {
			long responseTime = System.nanoTime() - performanceStartTime;
			responseTime = TimeUnit.NANOSECONDS.toMillis(responseTime);
			LOG.log(Level.SEVERE, "CancellationException: " + url + "; " + e.getMessage());
			generator.revertLastCall();
			result.setTransactionState(TransactionState.FAILED);
			result.setResponseTime(responseTime);
		} catch (InterruptedException e) {
			long responseTime = System.nanoTime() - performanceStartTime;
			responseTime = TimeUnit.NANOSECONDS.toMillis(responseTime);
			LOG.log(Level.SEVERE, "InterruptedException: " + e.getMessage());
			generator.revertLastCall();
			result.setTransactionState(TransactionState.FAILED);
			result.setResponseTime(responseTime);
		}


		return result;
	}

	@Override
	public void run() {
		HTTPInputGenerator generator = HTTPInputGeneratorPool.getPool().takeFromPool();
		HTTPTransactionResult result = this.process(generator);
		ResultTracker.TRACKER.logTransaction(result);
		HTTPInputGeneratorPool.getPool().releaseBackToPool(generator);
		TransactionQueueSingleton transactionQueue = TransactionQueueSingleton.getInstance();
		transactionQueue.addQueueElement(this);
	}

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
