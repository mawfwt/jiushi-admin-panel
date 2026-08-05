package com.jiushi.adminpanel.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 哈希工具类
 * <p>
 * 提供 SHA-256 哈希计算功能, 用于:
 * 1. 管理员邀请码验证 (码+盐)
 * 2. 兑换券防伪
 */
public final class HashUtils {

    private HashUtils() {}

    /**
     * 计算输入字符串的 SHA-256 哈希 (输出小写十六进制)
     * @param input 原始字符串 (UTF-8编码)
     * @return 64位十六进制哈希字符串
     */
    public static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) sb.append('0'); // 补齐单字节前导0
                sb.append(hex);
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }
}
