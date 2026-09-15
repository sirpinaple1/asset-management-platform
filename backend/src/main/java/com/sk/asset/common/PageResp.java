package com.sk.asset.common;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.Data;

import java.util.List;

/**
 * 统一分页响应（P3 契约：data 内嵌分页结构，前端按 records/total/page/size 解析）。
 */
@Data
public class PageResp<T> {

    private List<T> records;
    private long total;
    private long page;
    private long size;

    public static <E, T> PageResp<T> of(IPage<E> page, List<T> records) {
        PageResp<T> resp = new PageResp<>();
        resp.setRecords(records);
        resp.setTotal(page.getTotal());
        resp.setPage(page.getCurrent());
        resp.setSize(page.getSize());
        return resp;
    }
}
