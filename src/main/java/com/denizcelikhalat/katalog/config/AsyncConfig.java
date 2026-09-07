package com.denizcelikhalat.katalog.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * @EnableAsync: EmailService içindeki @Async metotların ayrı bir thread havuzunda
 * (arka planda) çalışmasını etkinleştirir. Bu sınıf olmazsa @Async görmezden gelinir
 * ve mail gönderimi checkout isteğini bloklar.
 */
@Configuration
@EnableAsync
public class AsyncConfig {
}
