import cn.dev33.satoken.dao.SaTokenDaoRedisJackson;
import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.github.lianjiatech.retrofit.spring.boot.core.RetrofitScan;
import com.shop.mq.config.MqConfig;
import com.shop.mq.producer.MqProducer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;

/**
 * @author xxj
 * @title MqTest
 * @date 2026/10/31 12:50
 * @description TODO
 */
@SpringBootApplication(scanBasePackages = {"com.shop.mq"},
        exclude = {
                DataSourceAutoConfiguration.class,
                MybatisPlusAutoConfiguration.class,
                RedisAutoConfiguration.class,
                org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration.class,
                SaTokenDaoRedisJackson.class
        })
@ComponentScan(
        basePackages = {"com.shop.mq"}
)
@RetrofitScan("com.shop.mq")
@SpringBootTest(classes = MqTest.class)
public class MqTest {

    @Autowired
    private MqProducer mqProducer;

    @Test
    public void test(){
        // 发给 queue1
        mqProducer.sendDirect(MqConfig.DIRECT_ROUTING_KEY_1, "hello queue1");

        // 发给 queue2
        mqProducer.sendDirect(MqConfig.DIRECT_ROUTING_KEY_2, "hello queue2");
    }

}
