package com.xhzb.oss.config;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.CannedAccessControlList;
import com.aliyun.oss.model.CreateBucketRequest;
import com.aliyun.oss.model.SetBucketLoggingRequest;
import com.xhzb.oss.properties.AliOssConfigProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class OssAliyunAutoConfig {

    @Autowired
    AliOssConfigProperties aliOssConfigProperties;

    @Bean
    public OSS ossClient(){
        log.info("-----------------开始创建OSSClient--------------------");
        OSS ossClient = new OSSClientBuilder().build(aliOssConfigProperties.getEndpoint(),
                aliOssConfigProperties.getAccessKeyId(), aliOssConfigProperties.getAccessKeySecret());
        //桶初始化属于一次性运维动作，权限不足时只告警，不阻塞应用启动
        try {
            //判断容器是否存在,不存在就创建
            if (!ossClient.doesBucketExist(aliOssConfigProperties.getBucketName())) {
                CreateBucketRequest createBucketRequest = new CreateBucketRequest(aliOssConfigProperties.getBucketName());
                //设置问公共可读
                createBucketRequest.setCannedACL(CannedAccessControlList.PublicRead);
                ossClient.createBucket(createBucketRequest);
            }

            //添加客户端访问日志
            SetBucketLoggingRequest request = new SetBucketLoggingRequest(aliOssConfigProperties.getBucketName());
            // 设置存放日志文件的存储空间。
            request.setTargetBucket(aliOssConfigProperties.getBucketName());
            // 设置日志文件存放的目录。
            request.setTargetPrefix(aliOssConfigProperties.getBucketName());
            ossClient.setBucketLogging(request);
        } catch (Exception e) {
            log.warn("OSS 桶初始化失败，已跳过（文件上传将不可用）：{}", e.getMessage());
        }

        log.info("-----------------结束创建OSSClient--------------------");
        return ossClient;
    }


}
