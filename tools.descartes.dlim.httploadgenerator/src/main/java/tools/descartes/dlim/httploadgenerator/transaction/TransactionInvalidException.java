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
package tools.descartes.dlim.httploadgenerator.transaction;

/**
 * Exception for invalid/failed transactions.
 * Differs from dropped transaction in that these transactions were actually executed but failed.
 * E.g. the received an error status code.
 * @author Joakim von Kistowksi
 *
 */
public class TransactionInvalidException extends Exception {

	public final long responseTime;

	public TransactionInvalidException(String message, long responseTime) {
		super(message);
		this.responseTime = responseTime;
	}

	private static final long serialVersionUID = -4004921011216381942L;
}
