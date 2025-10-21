package tools.descartes.dlim.httploadgenerator.http;
import static org.junit.jupiter.api.Assertions.*;
import tools.descartes.dlim.httploadgenerator.generator.ResultTracker.TransactionState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.luaj.vm2.*;
import org.luaj.vm2.lib.jse.JsePlatform;
import org.luaj.vm2.lib.OneArgFunction;
import org.luaj.vm2.lib.ZeroArgFunction;

public class HTTPInputGeneratorTest {

    private HTTPInputGenerator generator;
    private Globals globalsMock;

    @BeforeEach
    void setup() {
        // Create a mock Lua environment
        globalsMock = JsePlatform.standardGlobals();

        // Mock "onCall" function with controlled behavior:
        LuaValue onCall = new OneArgFunction() {

            @Override
            public LuaValue call(LuaValue arg) {
                int callNum = arg.toint();
                if (callNum == 3) {
                    return LuaValue.NIL;
                }
                return LuaValue.valueOf("URL-" + callNum);
            }
        };

        globalsMock.set("onCall", onCall);
        globalsMock.set("onCycle", new ZeroArgFunction() {
            @Override
            public LuaValue call() {
                return LuaValue.NONE;
            }
        });

        generator = new HTTPInputGenerator(1, null, 0, 0, null);
        generator.setLuaGlobals(globalsMock); // inject our fake Lua environment

    }

    @Test
    void testGetCurrentCallNumCachesResult() {
        int callNum = generator.getCurrentCallNum();
        assertEquals(1, callNum, "First callNum should start at 1");

        String firstInput = generator.getNextInput();
        assertEquals("URL-1", firstInput, "getNextInput should return URL of call 1");

        // Ensure cycle did not restart yet
        assertEquals(2, generator.getCurrentCallNum(), "Next callNum should be 2 after using first input");
        String secondInput = generator.getNextInput();
        assertEquals("URL-2", secondInput, "getNextInput should return URL of call 2");
    }

    @Test
    void testMultipleSequentialCallsGetNextInput() {
        String input1 = generator.getNextInput();
        String input2 = generator.getNextInput();
        assertEquals("URL-1", input1);
        assertEquals("URL-2", input2);
        assertEquals(1, generator.getCurrentCallNum());
    }

    @Test
    void testMultipleSequentialCallsGetCurrentCallNum() {
        assertEquals(1, generator.getCurrentCallNum());
        assertEquals(1, generator.getCurrentCallNum());
        assertEquals(1, generator.getCurrentCallNum());
        assertEquals(1, generator.getCurrentCallNum());
        String input1 = generator.getNextInput();
        String input2 = generator.getNextInput();
        assertEquals("URL-1", input1);
        assertEquals("URL-2", input2);
        assertEquals(1, generator.getCurrentCallNum());
    }

    @Test
    void testCycleRestartWhenLuaReturnsNil() {
        generator.getNextInput(); // 1
        generator.getNextInput(); // 2

        int currentNum = generator.getCurrentCallNum();
        assertEquals(1, currentNum, "After cycle restart, currentCallNum should be 1");
        String input3 = generator.getNextInput(); // would trigger restart, because callNum 4 -> nil
        assertEquals("URL-1", input3, "Cycle restarts to URL-1 after nil return");

    }

    @Test
    void testInterleavingGetCurrentCallNumAndGetNextInput() {
        assertEquals(1, generator.getCurrentCallNum());
        assertEquals("URL-1", generator.getNextInput());

        assertEquals("URL-2", generator.getNextInput());

        assertEquals("URL-1", generator.getNextInput());
        assertEquals(2, generator.getCurrentCallNum());
        assertEquals(2, generator.getCurrentCallNum());
        assertEquals("URL-2", generator.getNextInput());
        assertEquals(1, generator.getCurrentCallNum());
    }

    @Test
    void testDroppedRequestNoRetry() {
        // No Retries
        generator.setMaxTries(0);

        // Currently we are on Call #1
        assertEquals(1, generator.getCurrentCallNum());
        // And have no retries
        assertEquals(0, generator.getRetries());

        // Dropped request
        HTTPTransaction.resetGeneratorBasedOnTransactionState(TransactionState.DROPPED, generator);

        // We do not retry any request, so we are on Call #2
        assertEquals(2, generator.getCurrentCallNum());
        // And still have no retries
        assertEquals(0, generator.getRetries());

        // Dropped request
        HTTPTransaction.resetGeneratorBasedOnTransactionState(TransactionState.DROPPED, generator);

        // Call cycle has only 2 requests, so back to Call #1
        assertEquals(1, generator.getCurrentCallNum());

        // Dropped request
        HTTPTransaction.resetGeneratorBasedOnTransactionState(TransactionState.DROPPED, generator);

        // We do not retry any request, so we are on Call #2
        assertEquals(2, generator.getCurrentCallNum());
        // And still have no retries
        assertEquals(0, generator.getRetries());
    }

    @Test
    void testFailedRequestNoRetry() {
        // No Retries
        generator.setMaxTries(0);

        // Currently we are on Call #1
        assertEquals(1, generator.getCurrentCallNum());
        // And have no retries
        assertEquals(0, generator.getRetries());

        // Failed request
        generator.getNextInput();
        HTTPTransaction.resetGeneratorBasedOnTransactionState(TransactionState.FAILED, generator);

        // We do not retry any request, so we are on Call #2
        assertEquals(2, generator.getCurrentCallNum());
        // And still have no retries
        assertEquals(0, generator.getRetries());

        // Failed request
        generator.getNextInput();
        HTTPTransaction.resetGeneratorBasedOnTransactionState(TransactionState.FAILED, generator);

        // We do not retry any request, so we are on to the next call
        // Call cycle has only 2 requests, so back to Call #1
        assertEquals(1, generator.getCurrentCallNum());
        // And still have no retries
        assertEquals(0, generator.getRetries());
    }

    @Test
    void testSuccessfulRequestNoRetry() {
        // No Retries
        generator.setMaxTries(0);

        // Currently we are on Call #1
        assertEquals(1, generator.getCurrentCallNum());
        // And have no retries
        assertEquals(0, generator.getRetries());

        // Successful request
        generator.getNextInput();
        HTTPTransaction.resetGeneratorBasedOnTransactionState(TransactionState.SUCCESS, generator);

        // Currently we are on Call #2
        assertEquals(2, generator.getCurrentCallNum());
        // And have no retries
        assertEquals(0, generator.getRetries());

        // Successful request
        generator.getNextInput();
        HTTPTransaction.resetGeneratorBasedOnTransactionState(TransactionState.SUCCESS, generator);

        // Call cycle has only 2 requests, so back to Call #1
        assertEquals(1, generator.getCurrentCallNum());
        // And still have no retries
        assertEquals(0, generator.getRetries());
    }

    @Test
    void testSuccessfulFailedMixNoRetry() {
        // No Retries
        generator.setMaxTries(0);

        // Currently we are on Call #1
        assertEquals(1, generator.getCurrentCallNum());
        // And have no retries
        assertEquals(0, generator.getRetries());

        // Successful request
        generator.getNextInput();
        HTTPTransaction.resetGeneratorBasedOnTransactionState(TransactionState.SUCCESS, generator);

        // Currently we are on Call #2
        assertEquals(2, generator.getCurrentCallNum());
        // And have no retries
        assertEquals(0, generator.getRetries());

        // Failed request
        generator.getNextInput();
        HTTPTransaction.resetGeneratorBasedOnTransactionState(TransactionState.FAILED, generator);

        // We do not retry any request, so we are on to the next call
        // Call cycle has only 2 requests, so back to Call #1
        assertEquals(1, generator.getCurrentCallNum());
        // And still have no retries
        assertEquals(0, generator.getRetries());

        // Failed request
        generator.getNextInput();
        HTTPTransaction.resetGeneratorBasedOnTransactionState(TransactionState.FAILED, generator);

        // We do not retry any request, so we are on to the next call (Call #2)
        assertEquals(2, generator.getCurrentCallNum());
        // And still have no retries
        assertEquals(0, generator.getRetries());

        // Successful request
        generator.getNextInput();
        HTTPTransaction.resetGeneratorBasedOnTransactionState(TransactionState.SUCCESS, generator);

        // Currently we are on Call #1
        assertEquals(1, generator.getCurrentCallNum());
        // And have no retries
        assertEquals(0, generator.getRetries());
    }

    @Test
    void testDroppedRequestWithRetry() {
        // Try each request at most 2 times
        generator.setMaxTries(2);

        // Currently we are on Call #1
        assertEquals(1, generator.getCurrentCallNum());
        // And have no retries
        assertEquals(0, generator.getRetries());

        // Dropped request
        HTTPTransaction.resetGeneratorBasedOnTransactionState(TransactionState.DROPPED, generator);

        // We try every request 2 times, so we are still on Call #1
        assertEquals(1, generator.getCurrentCallNum());
        // And are on retry #1
        assertEquals(1, generator.getRetries());

        // Dropped request
        HTTPTransaction.resetGeneratorBasedOnTransactionState(TransactionState.DROPPED, generator);

        // We tried Call #1 two times, so we are on the next call now (Call #2)
        assertEquals(2, generator.getCurrentCallNum());
        // And have no retries on this call
        assertEquals(0, generator.getRetries());

        // Dropped request
        HTTPTransaction.resetGeneratorBasedOnTransactionState(TransactionState.DROPPED, generator);

        // We try every request 2 times, so we are still on Call #2
        assertEquals(2, generator.getCurrentCallNum());
        // And are on retry #1
        assertEquals(1, generator.getRetries());

        // Dropped request
        HTTPTransaction.resetGeneratorBasedOnTransactionState(TransactionState.DROPPED, generator);

        // We tried Call #2 two times, so we are on the next call now
        // Call cycle has only 2 requests, so back to Call #1
        assertEquals(1, generator.getCurrentCallNum());
        // And have no retries
        assertEquals(0, generator.getRetries());

        // Dropped request
        HTTPTransaction.resetGeneratorBasedOnTransactionState(TransactionState.DROPPED, generator);

        // We try every request 2 times, so we are still on Call #1
        assertEquals(1, generator.getCurrentCallNum());
        // And are on retry #1
        assertEquals(1, generator.getRetries());
    }

    @Test
    void testFailedRequestWithRetry() {
        // Try each request at most 2 times
        generator.setMaxTries(2);

        // Currently we are on Call #1
        assertEquals(1, generator.getCurrentCallNum());
        // And have no retries
        assertEquals(0, generator.getRetries());

        // Failed request
        generator.getNextInput();
        HTTPTransaction.resetGeneratorBasedOnTransactionState(TransactionState.FAILED, generator);

        // We try every request 2 times, so we are still on Call #1
        assertEquals(1, generator.getCurrentCallNum());
        // And are on retry #1
        assertEquals(1, generator.getRetries());

        // Failed request
        generator.getNextInput();
        HTTPTransaction.resetGeneratorBasedOnTransactionState(TransactionState.FAILED, generator);

        // We tried Call #1 two times, so we are on the next call now (Call #2)
        assertEquals(2, generator.getCurrentCallNum());
        // And have no retries on this call
        assertEquals(0, generator.getRetries());

        // Failed request
        generator.getNextInput();
        HTTPTransaction.resetGeneratorBasedOnTransactionState(TransactionState.FAILED, generator);

        // We try every request 2 times, so we are still on Call #2
        assertEquals(2, generator.getCurrentCallNum());
        // And are on retry #1
        assertEquals(1, generator.getRetries());

        // Failed request
        generator.getNextInput();
        HTTPTransaction.resetGeneratorBasedOnTransactionState(TransactionState.FAILED, generator);

        // We tried Call #2 two times, so we are on the next call now
        // Call cycle has only 2 requests, so back to Call #1
        assertEquals(1, generator.getCurrentCallNum());
        // And have no retries
        assertEquals(0, generator.getRetries());

        // Failed request
        generator.getNextInput();
        HTTPTransaction.resetGeneratorBasedOnTransactionState(TransactionState.FAILED, generator);

        // We try every request 2 times, so we are still on Call #1
        assertEquals(1, generator.getCurrentCallNum());
        // And are on retry #1
        assertEquals(1, generator.getRetries());
    }

    @Test
    void testSuccessfulRequestWithRetry() {
        // Try each request at most 2 times
        generator.setMaxTries(2);

        // Currently we are on Call #1
        assertEquals(1, generator.getCurrentCallNum());
        // And have no retries
        assertEquals(0, generator.getRetries());

        // Successful request
        generator.getNextInput();
        HTTPTransaction.resetGeneratorBasedOnTransactionState(TransactionState.SUCCESS, generator);

        // Call #1 was successful, so we are on the next call now (Call #2)
        assertEquals(2, generator.getCurrentCallNum());
        // And have no retries on this call
        assertEquals(0, generator.getRetries());

        // Successful request
        generator.getNextInput();
        HTTPTransaction.resetGeneratorBasedOnTransactionState(TransactionState.SUCCESS, generator);

        // Call #2 was successful, so we are on the next call now
        // Call cycle has only 2 requests, so back to Call #1
        assertEquals(1, generator.getCurrentCallNum());
        // And have no retries
        assertEquals(0, generator.getRetries());

        // Successful request
        generator.getNextInput();
        HTTPTransaction.resetGeneratorBasedOnTransactionState(TransactionState.SUCCESS, generator);

        // Call #1 was successful, so we are on the next call now (Call #2)
        assertEquals(2, generator.getCurrentCallNum());
        // And have no retries on this call
        assertEquals(0, generator.getRetries());
    }

    @Test
    void testFailedSuccessfulMixWithRetry() {
        // Try each request at most 2 times
        generator.setMaxTries(2);

        // Currently we are on Call #1
        assertEquals(1, generator.getCurrentCallNum());
        // And have no retries
        assertEquals(0, generator.getRetries());

        // Failed request
        generator.getNextInput();
        HTTPTransaction.resetGeneratorBasedOnTransactionState(TransactionState.FAILED, generator);

        // We try every request 2 times, so we are still on Call #1
        assertEquals(1, generator.getCurrentCallNum());
        // And are on retry #1
        assertEquals(1, generator.getRetries());

        // Successful request
        generator.getNextInput();
        HTTPTransaction.resetGeneratorBasedOnTransactionState(TransactionState.SUCCESS, generator);

        // Call #1 was successful, so we are on the next call now (Call #2)
        assertEquals(2, generator.getCurrentCallNum());
        // And have no retries on this call
        assertEquals(0, generator.getRetries());

        // Failed request
        generator.getNextInput();
        HTTPTransaction.resetGeneratorBasedOnTransactionState(TransactionState.FAILED, generator);

        // We try every request 2 times, so we are still on Call #2
        assertEquals(2, generator.getCurrentCallNum());
        // And are on retry #1
        assertEquals(1, generator.getRetries());

        // Failed request
        generator.getNextInput();
        HTTPTransaction.resetGeneratorBasedOnTransactionState(TransactionState.FAILED, generator);

        // We tried Call #2 two times, so we are on the next call now
        // Call cycle has only 2 requests, so back to Call #1
        assertEquals(1, generator.getCurrentCallNum());
        // And have no retries
        assertEquals(0, generator.getRetries());
    }
}

