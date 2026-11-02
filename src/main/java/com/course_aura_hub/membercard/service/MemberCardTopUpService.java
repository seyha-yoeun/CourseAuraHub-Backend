package com.course_aura_hub.membercard.service;

import com.course_aura_hub.membercard.dto.MemberCardReportResponse;
import com.course_aura_hub.membercard.dto.MemberCardTopUpCreateRequest;
import com.course_aura_hub.membercard.dto.MemberCardTopUpPageResponse;
import com.course_aura_hub.membercard.dto.MemberCardTopUpResponse;

import java.util.List;
import java.util.UUID;

public interface MemberCardTopUpService {

    MemberCardTopUpResponse request(String email, MemberCardTopUpCreateRequest request);

    List<MemberCardTopUpResponse> findMine(String email);

    MemberCardTopUpPageResponse findAll(String status, int page, int size);

    MemberCardTopUpResponse approve(Integer requestId, UUID adminId);

    MemberCardTopUpResponse reject(Integer requestId, UUID adminId, String note);

    MemberCardReportResponse report(int days);
}
