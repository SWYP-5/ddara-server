package com.app.backend.domain.user.dto;

import java.util.List;

/** 카메라 가이드 열람 여부 조회 응답 */
public record CameraGuideResponse(List<String> seen) {
}