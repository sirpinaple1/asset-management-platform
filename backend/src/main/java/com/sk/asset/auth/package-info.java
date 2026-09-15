/**
 * 鉴权接入：AuthPort(HTTP 调 comm_public_basic)、TokenFilter、UserContext。
 * 鉴权统一复用 comm_public_basic，本系统不自建登录/用户/权限主数据（ADR-0004，R8 红线）。
 */
package com.sk.asset.auth;
