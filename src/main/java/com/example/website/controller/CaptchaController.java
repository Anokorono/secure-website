package com.example.website.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import javax.imageio.ImageIO;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.security.SecureRandom;

/**
 * 图形验证码控制器
 * 用于登录连续失败后强制校验，防止自动化暴力破解。
 * 验证码明文存入 session（不落地、不拼接可预测随机数）。
 */
@Controller
@RequestMapping("/auth")
public class CaptchaController {

    public static final String SESSION_KEY = "LOGIN_CAPTCHA";

    private static final int WIDTH = 150;
    private static final int HEIGHT = 50;
    private static final int LEN = 4;

    /** 验证码字符集：去除易混淆的 0/O、1/I/L */
    private static final char[] CHARS = "23456789ABCDEFGHJKMNPQRSTUVWXYZ".toCharArray();

    private static final SecureRandom RANDOM = new SecureRandom();

    @GetMapping("/captcha")
    public void captcha(HttpServletRequest request, HttpServletResponse response) throws IOException {
        // 禁止缓存
        response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate");
        response.setHeader("Pragma", "no-cache");
        response.setDateHeader("Expires", 0);
        response.setContentType("image/png");

        String code = randomCode(LEN);

        // 存入 session（会话维度，绑定客户端）
        HttpSession session = request.getSession(true);
        session.setAttribute(SESSION_KEY, code);

        BufferedImage image = render(code);
        ImageIO.write(image, "png", response.getOutputStream());
    }

    private String randomCode(int len) {
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            sb.append(CHARS[RANDOM.nextInt(CHARS.length)]);
        }
        return sb.toString();
    }

    /** 绘制带强干扰（旋转、扭曲、深色曲线、高密度噪点）的验证码图片，提升抗 OCR 能力 */
    private BufferedImage render(String code) {
        BufferedImage img = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // 1. 背景：浅色渐变，增加轻微噪点底色
        g.setPaint(new GradientPaint(0, 0, new Color(0xF5F7FB), WIDTH, HEIGHT, new Color(0xE8ECF4)));
        g.fillRect(0, 0, WIDTH, HEIGHT);

        // 2. 背景噪点（先铺一层，让背景不纯净）
        for (int i = 0; i < 120; i++) {
            g.setColor(new Color(200 + RANDOM.nextInt(50), 205 + RANDOM.nextInt(45), 220 + RANDOM.nextInt(35)));
            g.fillRect(RANDOM.nextInt(WIDTH), RANDOM.nextInt(HEIGHT), 1, 1);
        }

        // 3. 深色干扰曲线（比字符更深、更粗，干扰切割）
        g.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (int i = 0; i < 5; i++) {
            g.setColor(new Color(60 + RANDOM.nextInt(120), 60 + RANDOM.nextInt(120), 80 + RANDOM.nextInt(110)));
            int x1 = RANDOM.nextInt(WIDTH / 3);
            int y1 = RANDOM.nextInt(HEIGHT);
            int ctrlX = RANDOM.nextInt(WIDTH);
            int ctrlY = RANDOM.nextInt(HEIGHT);
            int x2 = WIDTH - RANDOM.nextInt(WIDTH / 3);
            int y2 = RANDOM.nextInt(HEIGHT);
            g.draw(new java.awt.geom.QuadCurve2D.Double(x1, y1, ctrlX, ctrlY, x2, y2));
        }

        // 4. 字符：随机旋转 + 位移，颜色随机构成，逐字绘制
        int cw = WIDTH / (code.length() + 1);
        for (int i = 0; i < code.length(); i++) {
            g.setColor(new Color(20 + RANDOM.nextInt(90), 30 + RANDOM.nextInt(80), 90 + RANDOM.nextInt(130)));
            int size = 28 + RANDOM.nextInt(10);
            g.setFont(new Font("SansSerif", Font.BOLD, size));

            // 每个字符随机旋转角度（-35° ~ +35°）
            double angle = Math.toRadians((RANDOM.nextInt(71) - 35));
            int cx = cw * (i + 1) - 8 + RANDOM.nextInt(6);
            int cy = 32 + RANDOM.nextInt(8);

            AffineTransform old = g.getTransform();
            g.rotate(angle, cx, cy);
            g.drawString(String.valueOf(code.charAt(i)), cx - size / 3, cy);
            g.setTransform(old);
        }

        // 5. 前景噪点（覆盖字符上方的干扰点，进一步破坏 OCR 分割）
        for (int i = 0; i < 60; i++) {
            g.setColor(new Color(80 + RANDOM.nextInt(120), 80 + RANDOM.nextInt(120), 90 + RANDOM.nextInt(120)));
            g.fillRect(RANDOM.nextInt(WIDTH), RANDOM.nextInt(HEIGHT), RANDOM.nextInt(2) + 1, RANDOM.nextInt(2) + 1);
        }

        g.dispose();
        return img;
    }

    /** 校验验证码（忽略大小写，一次性使用：校验后立即清除，防重放） */
    public static boolean verify(HttpServletRequest request, String input) {
        if (input == null || input.isEmpty()) {
            return false;
        }
        HttpSession session = request.getSession(false);
        if (session == null) {
            return false;
        }
        Object stored = session.getAttribute(SESSION_KEY);
        // 一次性：无论对错都清除，防止穷举验证码
        session.removeAttribute(SESSION_KEY);
        return stored != null && input.equalsIgnoreCase(stored.toString());
    }
}
