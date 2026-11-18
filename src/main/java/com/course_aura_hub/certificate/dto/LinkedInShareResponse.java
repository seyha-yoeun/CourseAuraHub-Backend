package com.course_aura_hub.certificate.dto;

/** @param url opens LinkedIn's "add license or certification" form, already filled in */
public record LinkedInShareResponse(String url, String certificateNumber, String verifyUrl) {
}
