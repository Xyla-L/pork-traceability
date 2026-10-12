package com.pork.distribution.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * 文件服务内部调用客户端。
 * <p>
 * 目前只有一处用途：车间监控快照滚动淘汰时，把被淘汰记录对应的文件一并删掉。
 * <p>
 * <b>失败只记日志、不抛异常</b>：删文件是「清磁盘」的运维动作，
 * 它失败不该让一次已经成功的业务写入回滚 —— 留几个孤儿文件不影响任何数据一致性，
 * 但把好好的快照业务带崩就是本末倒置。
 */
@Slf4j
@Component
public class FileClient {

    private final RestClient restClient;
    private final String fileUrl;

    public FileClient(RestClient.Builder builder,
                      @Value("${services.file-url:http://localhost:8088}") String fileUrl) {
        this.restClient = builder.build();
        this.fileUrl = fileUrl;
    }

    /** 删除文件服务上的文件；fileId 为空或服务不可用时静默跳过 */
    public void delete(String fileId) {
        if (fileId == null || fileId.isBlank()) return;
        try {
            restClient.delete().uri(fileUrl + "/file/{fileId}", fileId).retrieve().toBodilessEntity();
        } catch (Exception e) {
            log.warn("监控快照文件删除失败 fileId={}: {}", fileId, e.getMessage());
        }
    }
}
