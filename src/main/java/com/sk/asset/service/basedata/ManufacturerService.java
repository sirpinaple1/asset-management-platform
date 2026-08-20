package com.sk.asset.service.basedata;

import com.sk.asset.entity.basedata.Manufacturer;

import java.util.List;

/**
 * 厂商服务
 */
public interface ManufacturerService {

    List<Manufacturer> list();

    Manufacturer getById(Long id);

    void save(Manufacturer manufacturer);

    void updateById(Manufacturer manufacturer);

    void deleteById(Long id);
}
