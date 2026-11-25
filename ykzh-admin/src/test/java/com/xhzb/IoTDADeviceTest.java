package com.xhzb;

import com.huaweicloud.sdk.iotda.v5.IoTDAClient;
import com.huaweicloud.sdk.iotda.v5.model.ListProductsRequest;
import com.huaweicloud.sdk.iotda.v5.model.ListProductsResponse;
import com.huaweicloud.sdk.iotda.v5.model.ProductSummary;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

/**
 * 华为IoTDA接口测试
 *
 * @Author mr.wu
 * @Date 2025-5-9 17:20
 */
@SpringBootTest
public class IoTDADeviceTest {

    @Autowired
    private IoTDAClient client;


    @DisplayName("查询产品列表")
    @Test
    public void findProductList(){
        ListProductsRequest request = new ListProductsRequest();
        request.setLimit(10);
        ListProductsResponse response = client.listProducts(request);
        List<ProductSummary> products = response.getProducts();
        products.forEach(System.out::println);
    }
}