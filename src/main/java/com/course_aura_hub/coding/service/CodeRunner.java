package com.course_aura_hub.coding.service;

import java.math.BigDecimal;

/** Runs untrusted code in an isolated sandbox. This server never executes learners' code itself. */
public interface CodeRunner {

    /** @param languageId the sandbox's id for the language */
    RunOutcome run(int languageId, String code, String stdin, BigDecimal timeLimitSeconds, int memoryLimitMb);

    /**
     * @param status accepted (ran and exited normally), compile_error, runtime_error, time_limit or error
     */
    record RunOutcome(String status, String stdout, String stderr, String compileOutput) {
    }
}
