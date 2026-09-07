package com.denizcelikhalat.katalog.config;

import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Configuration;

/**
 * Tomcat multipart "part" sayısı sınırını yükseltir.
 *
 * Yeni Tomcat sürümlerinde (10.1.42+/9.0.107+) bir multipart isteğindeki toplam part sayısı
 * varsayılan olarak 10 ile sınırlıdır. Ürün ekle/düzenle formu çok sayıda alan + 2 dosya + CSRF
 * token içerdiğinden bu sınır aşılır ve "FileCountLimitExceededException" alınır.
 *
 * setProperty("maxPartCount", ...) jenerik connector ayarı olduğundan sürümden bağımsız derlenir;
 * application.properties'teki server.tomcat.max-part-count tanınmazsa bu sınıf devreye girer.
 */
@Configuration
public class WebServerConfig implements WebServerFactoryCustomizer<TomcatServletWebServerFactory> {

    @Override
    public void customize(TomcatServletWebServerFactory factory) {
        factory.addConnectorCustomizers(connector -> connector.setProperty("maxPartCount", "100"));
    }
}
