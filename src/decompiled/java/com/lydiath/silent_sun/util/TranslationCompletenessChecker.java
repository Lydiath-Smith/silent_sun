package com.lydiath.silent_sun.util;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.TreeSet;

/**
 * 全翻译完整性检查模块。
 * <p>
 * 读取打包进 jar 的 {@code assets/silent_sun/lang/zh_cn.json} 与
 * {@code en_us.json}，对比两份文件的键集合，找出任一语言缺失的翻译键并
 * 打印到日志。用于在开发/测试环境启动时自动发现「只加了一种语言」的遗漏。
 * <p>
 * 生产环境不调用本模块（避免每次启动产生额外 IO 与日志噪音），由
 * {@code SilentSunMod} 构造器在 {@code !FMLLoader.isProduction()} 时触发。
 */
public final class TranslationCompletenessChecker {

    private static final String LANG_PATH = "/assets/silent_sun/lang/";

    private TranslationCompletenessChecker() {}

    /**
     * 执行翻译完整性检查。
     *
     * @param logger 输出用的 logger（通常为 mod 主类的 LOGGER）
     */
    public static void check(Logger logger) {
        try {
            Set<String> zh = loadKeys("zh_cn.json");
            Set<String> en = loadKeys("en_us.json");

            if (zh.isEmpty() && en.isEmpty()) {
                logger.warn("[silent_sun] 翻译检查：未找到 lang 资源文件。");
                return;
            }

            Set<String> zhOnly = new TreeSet<>(zh);
            zhOnly.removeAll(en);
            Set<String> enOnly = new TreeSet<>(en);
            enOnly.removeAll(zh);

            if (zhOnly.isEmpty() && enOnly.isEmpty()) {
                logger.info("[silent_sun] 翻译检查通过：zh_cn 与 en_us 键一致（共 {} 个）。", zh.size());
                return;
            }

            for (String key : zhOnly) {
                logger.warn("[silent_sun] 翻译缺失：en_us 缺少键 {}", key);
            }
            for (String key : enOnly) {
                logger.warn("[silent_sun] 翻译缺失：zh_cn 缺少键 {}", key);
            }
        } catch (Exception e) {
            logger.warn("[silent_sun] 翻译检查失败：{}", e.toString());
        }
    }

    private static Set<String> loadKeys(String fileName) throws Exception {
        Set<String> keys = new TreeSet<>();
        try (InputStream in = TranslationCompletenessChecker.class.getResourceAsStream(LANG_PATH + fileName)) {
            if (in == null) {
                return keys;
            }
            String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
            keys.addAll(obj.keySet());
        }
        return keys;
    }
}
