package com.denizcelikhalat.katalog.service;

import com.denizcelikhalat.katalog.model.PasswordResetToken;
import com.denizcelikhalat.katalog.model.User;
import com.denizcelikhalat.katalog.repository.PasswordResetTokenRepository;
import com.denizcelikhalat.katalog.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Optional;

/**
 * "Şifremi Unuttum / Şifre Sıfırlama" iş mantığı.
 *
 * GÜVENLİK NOTLARI:
 * - Ham token yalnızca e-posta linkinde bulunur; veritabanına yazılmaz, loglanmaz.
 *   Saklanan şey her zaman SHA-256 hash'idir (bkz. PasswordResetToken.tokenHash).
 * - requestPasswordReset(email): e-posta sistemde olsun/olmasın davranış ve süre farkı
 *   dışarıya sızdırılmaz; çağıran taraf (controller) her durumda aynı genel mesajı gösterir.
 * - Token 30 dakika geçerlidir ve kullanıldıktan sonra (used=true) bir daha kullanılamaz.
 */
@Service
public class PasswordResetService {

    private static final int TOKEN_BYTES = 32; // 256 bit - kriptografik olarak güvenli
    private static final long EXPIRY_MINUTES = 30;
    private static final int MIN_PASSWORD_LENGTH = 8;

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    public PasswordResetService(UserRepository userRepository,
                                PasswordResetTokenRepository tokenRepository,
                                PasswordEncoder passwordEncoder,
                                EmailService emailService) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
    }

    public enum TokenStatus { VALID, INVALID, EXPIRED, USED }

    public static class ResetResult {
        public boolean success;
        public boolean tokenProblem; // true: hata token'dan kaynaklanıyor (geçersiz/süresi dolmuş/kullanılmış)
        public String errorMessage;
    }

    /**
     * Şifre sıfırlama talebini işler. E-posta sistemde YOKSA hiçbir şey yapmadan sessizce döner
     * (istisna fırlatmaz, farklı bir gecikme yaratmaz) — controller her durumda aynı genel
     * "bağlantı gönderildi" mesajını gösterir, böylece e-postanın kayıtlı olup olmadığı sızdırılmaz.
     */
    @Transactional
    public void requestPasswordReset(String email) {
        if (email == null || email.isBlank()) {
            return;
        }

        Optional<User> userOpt = userRepository.findByEmail(email.trim());
        if (userOpt.isEmpty()) {
            return; // Güvenlik: kayıtlı olmayan e-postada da sessizce çık.
        }
        User user = userOpt.get();

        // Önceki (kullanılmamış) token'lar geçersiz kılınır; aynı anda tek link geçerli olur.
        tokenRepository.deleteAllByUserId(user.getId());

        String rawToken = generateSecureToken();
        String hash = sha256Hex(rawToken);

        PasswordResetToken entity = new PasswordResetToken();
        entity.setUser(user);
        entity.setTokenHash(hash);
        entity.setCreatedAt(LocalDateTime.now());
        entity.setExpiresAt(LocalDateTime.now().plusMinutes(EXPIRY_MINUTES));
        entity.setUsed(false);
        tokenRepository.save(entity);

        String resetLink = baseUrl + "/reset-password?token=" + rawToken;
        sendResetEmail(user, resetLink);

        // NOT: rawToken ve resetLink burada KASITLI olarak hiçbir yere loglanmıyor.
    }

    private void sendResetEmail(User user, String resetLink) {
        String fullName = ((user.getFirstName() != null ? user.getFirstName() : "") + " "
                + (user.getLastName() != null ? user.getLastName() : "")).trim();
        if (fullName.isBlank()) fullName = "Müşterimiz";

        String subject = "Şifre Sıfırlama Talebi | Deniz Çelik Halat";
        String body = "Sayın " + fullName + ",\n\n"
                + "Hesabınız için bir şifre sıfırlama talebinde bulunuldu.\n\n"
                + "Şifrenizi sıfırlamak için aşağıdaki bağlantıya tıklayın:\n"
                + resetLink + "\n\n"
                + "Bu bağlantı 30 dakika süreyle geçerlidir ve yalnızca bir kez kullanılabilir.\n\n"
                + "Bu talebi siz oluşturmadıysanız bu e-postayı görmezden gelebilirsiniz; "
                + "hesabınızda herhangi bir değişiklik yapılmayacaktır.\n\n"
                + "Deniz Çelik Halat\nBornova, İzmir";

        emailService.sendPlainText(user.getEmail(), subject, body);
    }

    /**
     * Token'ın (ham haliyle) geçerli olup olmadığını kontrol eder. GET /reset-password sayfası
     * formu göstermeden önce bunu çağırır.
     */
    @Transactional(readOnly = true)
    public TokenStatus checkToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return TokenStatus.INVALID;
        }
        String hash = sha256Hex(rawToken);
        Optional<PasswordResetToken> opt = tokenRepository.findByTokenHash(hash);
        if (opt.isEmpty()) {
            return TokenStatus.INVALID;
        }
        PasswordResetToken token = opt.get();
        if (Boolean.TRUE.equals(token.getUsed())) {
            return TokenStatus.USED;
        }
        if (token.getExpiresAt() == null || token.getExpiresAt().isBefore(LocalDateTime.now())) {
            return TokenStatus.EXPIRED;
        }
        return TokenStatus.VALID;
    }

    /**
     * Yeni şifreyi doğrular, token'ı yeniden (SUNUCU TARAFINDA) doğrular, PasswordEncoder ile
     * hash'leyip kullanıcıya kaydeder ve token'ı "used" olarak işaretler (tek kullanımlık).
     */
    @Transactional
    public ResetResult resetPassword(String rawToken, String newPassword, String confirmPassword) {
        ResetResult result = new ResetResult();

        // Önce token: parola kuralları hakkında bilgi vermeden önce bağlantının hâlâ
        // geçerli olduğundan emin olunur.
        TokenStatus status = checkToken(rawToken);
        if (status != TokenStatus.VALID) {
            result.success = false;
            result.tokenProblem = true;
            result.errorMessage = describeStatus(status);
            return result;
        }

        if (newPassword == null || newPassword.length() < MIN_PASSWORD_LENGTH) {
            result.success = false;
            result.errorMessage = "Şifre en az " + MIN_PASSWORD_LENGTH + " karakter olmalıdır.";
            return result;
        }
        if (!newPassword.equals(confirmPassword)) {
            result.success = false;
            result.errorMessage = "Şifreler eşleşmiyor.";
            return result;
        }

        String hash = sha256Hex(rawToken);
        PasswordResetToken token = tokenRepository.findByTokenHash(hash).orElse(null);
        if (token == null || token.getUser() == null) {
            result.success = false;
            result.tokenProblem = true;
            result.errorMessage = describeStatus(TokenStatus.INVALID);
            return result;
        }

        User user = token.getUser();
        user.setPassword(passwordEncoder.encode(newPassword)); // mevcut PasswordEncoder (BCrypt) ile
        userRepository.save(user);

        token.setUsed(true); // tek kullanımlık: bir daha bu token'la sıfırlama yapılamaz
        tokenRepository.save(token);

        result.success = true;
        return result;
    }

    public static String describeStatus(TokenStatus status) {
        if (status == null) {
            return "Bu bağlantı geçersiz. Lütfen yeni bir şifre sıfırlama talebinde bulunun.";
        }
        switch (status) {
            case EXPIRED:
                return "Bu bağlantının süresi dolmuş. Lütfen yeni bir şifre sıfırlama talebinde bulunun.";
            case USED:
                return "Bu bağlantı daha önce kullanılmış. Lütfen yeni bir şifre sıfırlama talebinde bulunun.";
            case VALID:
                return null;
            default:
                return "Bu bağlantı geçersiz. Lütfen yeni bir şifre sıfırlama talebinde bulunun.";
        }
    }

    // ===================== Yardımcılar (kriptografi) =====================

    // Kriptografik olarak güvenli, URL güvenli, dolgusuz Base64 random token (32 byte = 256 bit).
    private String generateSecureToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    // Saklama/karşılaştırma için tek yönlü SHA-256 hash (hex). Ham token asla saklanmaz.
    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 her JVM'de mevcuttur; pratikte asla gerçekleşmez.
            throw new IllegalStateException("SHA-256 algoritması bulunamadı", e);
        }
    }
}
