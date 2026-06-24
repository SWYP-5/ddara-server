package com.app.backend.domain.group.dto;

import java.util.List;

public record MyGroupsResponse(
        List<GroupListItem> groups
) {
}