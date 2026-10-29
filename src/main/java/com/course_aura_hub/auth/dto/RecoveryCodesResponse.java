package com.course_aura_hub.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Single-use recovery codes. Shown only once; store them safely")
public record RecoveryCodesResponse(
        List<String> recoveryCodes
) {}
