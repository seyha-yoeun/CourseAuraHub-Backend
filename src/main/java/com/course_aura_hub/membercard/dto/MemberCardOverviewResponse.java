package com.course_aura_hub.membercard.dto;

/**
 * @param card        null until an application is approved
 * @param application the latest application, or null if there has been none
 */
public record MemberCardOverviewResponse(MemberCardResponse card, MemberCardApplicationResponse application) {
}
