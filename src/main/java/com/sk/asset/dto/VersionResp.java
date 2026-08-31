package com.sk.asset.dto;

import com.sk.asset.common.AppVersion;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 版本信息响应（GET /api/v1/version，前端帮助面板展示）。
 */
@Data
@Schema(description = "后端版本信息")
public class VersionResp {

    @Schema(description = "后端版本号")
    private String version;

    @Schema(description = "发布日期（yyyy-MM-dd）")
    private String releaseDate;

    @Schema(description = "本版本新增功能")
    private List<String> changelog;

    @Schema(description = "版权署名")
    private String credit;

    public static VersionResp fromAppVersion() {
        VersionResp resp = new VersionResp();
        resp.setVersion(AppVersion.VERSION);
        resp.setReleaseDate(AppVersion.RELEASE_DATE);
        resp.setChangelog(AppVersion.CHANGELOG);
        resp.setCredit(AppVersion.CREDIT);
        return resp;
    }
}
