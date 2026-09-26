package io.github.mangomaner.mangobot.system.db;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 插件注册。
 *
 * <h3>为什么必须有这个类</h3>
 * MyBatis-Plus 的分页**不是**自动的：{@code IService.page(...)} 只有在装了
 * {@link PaginationInnerInterceptor} 时才会把 {@code LIMIT} 拼进 SQL。不装的话它既不报错也不警告，
 * 而是**把整张表查出来**、再在内存里截出当前页——代码看着完全正常，只是每次翻页都在拉全表。
 *
 * <p>这个坑在本项目里是真实发生过的：表情管理页第一次拉列表，1855 张图的接口把 1855 行全查了。
 * 因为在此之前全项目只有 {@code ImageController} 一处用到 {@code page()}，而那个接口没有页面在调，
 * 所以一直没暴露。
 *
 * <h3>为什么是 SQLITE</h3>
 * {@link DbType} 决定分页方言（LIMIT/OFFSET 的写法），跟本项目的数据库一致。
 * 将来若换库，这里要跟着改。
 *
 * <p>另注：3.5.9 起 JSqlParser 被拆成独立依赖，本插件需要它——见 {@code pom.xml} 里
 * {@code mybatis-plus-jsqlparser} 那条的注释。
 */
@Configuration
public class MybatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.SQLITE));
        return interceptor;
    }
}
