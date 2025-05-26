/**
 * Copyright 2025 Yannik Lubas
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
package tools.descartes.dlim.httploadgenerator.runner;


 /**
  * Container for per request interval results received by the director.
  * @author Yannik Lubas
  *
  */
public class PerRequestIntervalResult {

	private int requestNum;
	private String requestURI;
	private String method;
	private double responseTime;
	private String transactionState;
	private double transactionStartTime;
	private double targetTime;


	public double getTargetTime() {
		return targetTime;
	}
	public PerRequestIntervalResult(double targetTime, int requestNum, String requestURI, String method, double responseTime, String transactionState, double transactionStartTime) {
		this.targetTime = targetTime;
		this.requestNum = requestNum;
		this.requestURI = requestURI;
		this.method = method;
		this.responseTime = responseTime;
		this.transactionState = transactionState;
		this.transactionStartTime = transactionStartTime;
	}


	public int getRequestNum() {
		return requestNum;
	}

	public String getRequestURI() {
		return requestURI;
	}

	public String getMethod() {
		return method;
	}

	public double getResponseTime() {
		return responseTime;
	}

	public String getTransactionState() {
		return transactionState;
	}

	public double getTransactionStartTime() {
		return transactionStartTime;
	}

}
