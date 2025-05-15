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
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.api.Request;

import tools.descartes.dlim.httploadgenerator.generator.ResultTracker;
import tools.descartes.dlim.httploadgenerator.transaction.Transaction;
import tools.descartes.dlim.httploadgenerator.transaction.TransactionDroppedException;
import tools.descartes.dlim.httploadgenerator.transaction.TransactionInvalidException;
import tools.descartes.dlim.httploadgenerator.transaction.TransactionTimeoutException;
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
	public long process(HTTPInputGenerator generator) throws TransactionDroppedException, TransactionInvalidException, TransactionTimeoutException {
		long processStartTime = System.currentTimeMillis();
		if (generator.getTimeout() > 0 && processStartTime - getStartTime() > generator.getTimeout()) {
			throw new TransactionDroppedException("Wait time in queue too long. "
					+ String.valueOf(processStartTime - getStartTime())
					+ " ms passed before transaction was even started.");
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

		try {
			ContentResponse response = request.send();
			if (response.getStatus() >= 400) {
				long responseTime = System.currentTimeMillis() - processStartTime;
				generator.revertLastCall();
				LOG.log(Level.FINEST, "Received error response code: " + response.getStatus());
				throw new TransactionInvalidException("Error code: " + response.getStatus(), responseTime);
			} else {
				String responseBody = response.getContentAsString();
				long responseTime = System.currentTimeMillis() - processStartTime;

				// store result
				generator.resetHTMLFunctions(responseBody);
				return responseTime;
			}
		} catch (TimeoutException e) {
			generator.revertLastCall();
			throw new TransactionTimeoutException("TimeoutException: " + e.getMessage());
		} catch (ExecutionException e) {
			long responseTime = System.currentTimeMillis() - processStartTime;
			if (e.getCause() == null || !(e.getCause() instanceof TimeoutException)) {
				LOG.log(Level.SEVERE,
						"ExecutionException in call for URL: " + url + "; Cause: " + e.getCause().toString());
			}
			generator.revertLastCall();
			throw new TransactionInvalidException("ExecutionException: " + e.getMessage(), responseTime);
		} catch (CancellationException e) {
			long responseTime = System.currentTimeMillis() - processStartTime;
			LOG.log(Level.SEVERE, "CancellationException: " + url + "; " + e.getMessage());
			generator.revertLastCall();
			throw new TransactionInvalidException("CancellationException: " + e.getMessage(), responseTime);
		} catch (InterruptedException e) {
			long responseTime = System.currentTimeMillis() - processStartTime;
			LOG.log(Level.SEVERE, "InterruptedException: " + e.getMessage());
			generator.revertLastCall();
			throw new TransactionInvalidException("InterruptedException: " + e.getMessage(), responseTime);
		}
	}

	@Override
	public void run() {
		HTTPInputGenerator generator = HTTPInputGeneratorPool.getPool().takeFromPool();
		try {
			long responseTime = this.process(generator);
			ResultTracker.TRACKER.logTransaction(responseTime, ResultTracker.TransactionState.SUCCESS);
		} catch (TransactionDroppedException e) {
			ResultTracker.TRACKER.logTransaction(0, ResultTracker.TransactionState.DROPPED);
		} catch (TransactionInvalidException e) {
			ResultTracker.TRACKER.logTransaction(e.responseTime, ResultTracker.TransactionState.FAILED);
		} catch (TransactionTimeoutException e) {
			ResultTracker.TRACKER.logTransaction(generator.getTimeout(), ResultTracker.TransactionState.TIMEOUT);
		}
		HTTPInputGeneratorPool.getPool().releaseBackToPool(generator);
		TransactionQueueSingleton transactionQueue = TransactionQueueSingleton.getInstance();
		transactionQueue.addQueueElement(this);
	}
}
