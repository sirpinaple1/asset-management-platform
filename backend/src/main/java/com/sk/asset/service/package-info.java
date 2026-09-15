/**
 * 业务层：Service + Impl，包结构镜像 controller。
 * 跨上下文 service 调用须显式注入、不得双向循环依赖；跨上下文数据只通过 ID 引用（P2）。
 */
package com.sk.asset.service;
