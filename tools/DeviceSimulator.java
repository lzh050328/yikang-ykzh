import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/**
 * 华为云 IoTDA 设备模拟器（常驻）
 * <p>
 * 作用：让设备与平台保持 MQTT 长连接，IoTDA 控制台/接口里设备状态才会是「在线」，
 * 并周期性上报属性，影子数据随之刷新。
 * <p>
 * 鉴权规则（一机一密）：
 * ClientId = {deviceId}_0_0_{UTC yyyyMMddHH}
 * UserName = deviceId
 * Password = HMACSHA256(key=时间戳, content=secret) 小写十六进制
 * <p>
 * 用法：
 * java DeviceSimulator <deviceId> <secret> <serviceId> <属性key=value,...> [...更多设备]
 * 属性值：纯数字 -> 数字；true/false -> 布尔；其他 -> 字符串
 * 例：BodyTemp=36,xueyang=98,HeartRate=72,BatteryPercentage=90
 */
public class DeviceSimulator {

    private static final String BROKER_URI =
            "tcp://57c9731b73.st1.iotda-device.cn-north-4.myhuaweicloud.com:1883";
    /** 上报间隔（毫秒） */
    private static final long REPORT_INTERVAL_MS = 30_000L;

    public static void main(String[] args) throws Exception {
        if (args.length < 4 || args.length % 4 != 0) {
            System.out.println("用法: DeviceSimulator <deviceId> <secret> <serviceId> <key=value,...> [更多设备...]");
            return;
        }

        List<Device> devices = new ArrayList<>();
        for (int i = 0; i + 3 < args.length; i += 4) {
            devices.add(new Device(args[i], args[i + 1], args[i + 2], args[i + 3]));
        }

        for (Device d : devices) {
            d.connect();
        }
        System.out.println("[READY] 共 " + devices.size() + " 台设备已连接，每 "
                + (REPORT_INTERVAL_MS / 1000) + " 秒上报一次");

        while (true) {
            for (Device d : devices) {
                d.report();
            }
            Thread.sleep(REPORT_INTERVAL_MS);
        }
    }

    static class Device {
        final String deviceId;
        final String secret;
        final String serviceId;
        final String propSpec;
        MqttClient client;

        Device(String deviceId, String secret, String serviceId, String propSpec) {
            this.deviceId = deviceId;
            this.secret = secret;
            this.serviceId = serviceId;
            this.propSpec = propSpec;
        }

        void connect() throws Exception {
            String timestamp = ZonedDateTime.now(ZoneOffset.UTC)
                    .format(DateTimeFormatter.ofPattern("yyyyMMddHH"));
            // 签名类型 0：HMACSHA256 不校验时间戳；ClientId 仍必须带时间戳
            String clientId = deviceId + "_0_0_" + timestamp;
            String password = hmacSha256(timestamp, secret);

            client = new MqttClient(BROKER_URI, clientId, new MemoryPersistence());
            MqttConnectOptions options = new MqttConnectOptions();
            options.setUserName(deviceId);
            options.setPassword(password.toCharArray());
            options.setCleanSession(true);
            options.setKeepAliveInterval(60);
            options.setConnectionTimeout(15);
            options.setAutomaticReconnect(true);
            client.connect(options);
            System.out.println("[ONLINE] " + deviceId + " 已连接");
        }

        void report() {
            try {
                if (client == null || !client.isConnected()) {
                    System.out.println("[WARN] " + deviceId + " 连接已断开，重连中");
                    connect();
                }
                String payload = "{\"services\":[{\"service_id\":\"" + serviceId
                        + "\",\"properties\":" + toJson(propSpec) + "}]}";
                String topic = "$oc/devices/" + deviceId + "/sys/properties/report";
                client.publish(topic, payload.getBytes(StandardCharsets.UTF_8), 0, false);
                System.out.println("[REPORT] " + deviceId + " -> " + payload);
            } catch (Exception e) {
                System.out.println("[ERROR] " + deviceId + " 上报失败: " + e.getMessage());
            }
        }

        /** 把 BodyTemp=36,xueyang=98 转成 {"BodyTemp":36,"xueyang":98} */
        static String toJson(String spec) {
            StringBuilder sb = new StringBuilder("{");
            for (String pair : spec.split(",")) {
                String[] kv = pair.split("=", 2);
                if (kv.length != 2) {
                    continue;
                }
                if (sb.length() > 1) {
                    sb.append(",");
                }
                String key = kv[0].trim();
                String val = kv[1].trim();
                sb.append("\"").append(key).append("\":");
                if (val.matches("-?\\d+(\\.\\d+)?")) {
                    sb.append(val);                       // 数字
                } else if ("true".equalsIgnoreCase(val) || "false".equalsIgnoreCase(val)) {
                    sb.append(val.toLowerCase());         // 布尔
                } else {
                    sb.append("\"").append(val).append("\""); // 字符串
                }
            }
            return sb.append("}").toString();
        }
    }

    static String hmacSha256(String key, String content) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(content.getBytes(StandardCharsets.UTF_8)));
    }
}
