package com.pork.ingest.support;

/**
 * 一次上报的处理结论。
 * <p>
 * 只有 {@link #status()} == {@link IngestStatus#ACCEPTED} 才真正写入了业务表；
 * 其余状态都只留在待确认队列里，保证「采集 ≠ 生效」。
 */
public record DispatchResult(int status, Long targetId, String targetTable, String message) {

    public static DispatchResult accepted(Long targetId, String targetTable) {
        return new DispatchResult(IngestStatus.ACCEPTED, targetId, targetTable, "已入账");
    }

    public static DispatchResult manual(String targetTable, String message) {
        return new DispatchResult(IngestStatus.MANUAL, null, targetTable, message);
    }

    public static DispatchResult rejected(String targetTable, String message) {
        return new DispatchResult(IngestStatus.REJECTED, null, targetTable, message);
    }

    public static DispatchResult dropped(String targetTable, String message) {
        return new DispatchResult(IngestStatus.DROPPED, null, targetTable, message);
    }

    public boolean accepted() {
        return status == IngestStatus.ACCEPTED;
    }
}
