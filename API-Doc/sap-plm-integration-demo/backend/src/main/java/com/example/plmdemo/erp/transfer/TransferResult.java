package com.example.plmdemo.erp.transfer;

import com.example.plmdemo.erp.domain.TransferRecord;

/** Outcome of a submission. duplicate=true means an earlier transfer was returned instead of a new call. */
public record TransferResult(TransferRecord record, boolean duplicate) {
}
