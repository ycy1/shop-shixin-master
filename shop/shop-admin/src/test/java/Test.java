import cn.dev33.satoken.dao.SaTokenDaoRedisJackson;
import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.github.lianjiatech.retrofit.spring.boot.core.RetrofitScan;
import com.shop.client.HttpApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * @author xxj
 * @title Test
 * @date 2025/7/12 20:07
 * @description TODO
 */
@SpringBootApplication(scanBasePackages = {"com.shop.client"},
        exclude = {
                DataSourceAutoConfiguration.class,
                MybatisPlusAutoConfiguration.class,
                RedisAutoConfiguration.class,
                org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration.class,
                SaTokenDaoRedisJackson.class
        })
@ComponentScan(
        basePackages = {"com.shop.client"}
)
@RetrofitScan("com.shop.client") // 扫描该包下的 @RetrofitClient 接口
@SpringBootTest(classes = Test.class)
public class Test {

    public static void main(String[] args) {
//        System.out.println("hello world");
        try {
            String LIBWEBP_HOME = System.getenv("LIBWEBP_HOME");
            System.out.println("LIBWEBP_HOME: " + LIBWEBP_HOME);
            String outputPath = System.getProperty("user.dir")+ File.separator+"libwebp";
            System.out.println("项目目录: " + outputPath);

//            String libwebp ="F:\\assembly\\libwebp-1.5.0-windows-x64\\bin\\cwebp.exe";
            String cmd = "cwebp.exe -q 50 -mt C:\\Users\\Lenovo\\Desktop\\tlxVUGhoufiAewdaABDWF_WZUkI091.png -o "+ outputPath + File.separator +"output_file.webp";
            // 创建一个ProcessBuilder实例
//            ProcessBuilder builder = new ProcessBubilder("cmd", "", cmd);
            ProcessBuilder builder = new ProcessBuilder("cmd", "/c", LIBWEBP_HOME + cmd); // 使用dir命令列出当前目录的内容
            builder.redirectErrorStream(true); // 将错误输出和标准输出合并

            // 启动进程
            Process process = builder.start();

            // 读取命令的输出（如果有的话）
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), "GBK"));
            String line;
            while ((line = reader.readLine()) != null) {
//                String line1 = new String(line.getBytes("UTF-8"));
                System.out.println(line);
            }

            // 等待进程结束
            int exitCode = process.waitFor();
            System.out.println("Exited with code: " + exitCode);
            System.exit(0);
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

    }


    @Configuration
    static class TestConfig {
        @Bean
        public ObjectMapper objectMapper() {
            return Jackson2ObjectMapperBuilder.json()
                    .modules(new JavaTimeModule())
                    .build();
        }
    }

    @Autowired
    private HttpApi httpApi;

    @org.junit.jupiter.api.Test
    public void test() {
        System.out.println("hello world");
    }



}
