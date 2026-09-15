/**
 * REST 接口层。内部按限界上下文分包（asset/category/location/lifecycle/inventory/depreciation/consumption）。
 * 分层依赖 controller → service，禁止直调 mapper；entity 不外泄，须经 dto 转换（R1 红线）。
 */
package com.sk.asset.controller;
