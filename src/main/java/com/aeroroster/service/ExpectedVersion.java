package com.aeroroster.service;

import com.aeroroster.service.exception.StaleVersionException;

/** Original edit snapshot; row identity also distinguishes a deleted/recreated child link. */
public record ExpectedVersion(long id, long version) {
    public ExpectedVersion {
        if (id <= 0 || version < 0) throw new IllegalArgumentException("Invalid edit version");
    }

    static void check(ExpectedVersion expected, Long currentId, long currentVersion) {
        if (expected != null && (expected.id != currentId || expected.version != currentVersion)) {
            throw new StaleVersionException();
        }
    }
}
