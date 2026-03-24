package com.smart.office.base.vector;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.milvus.client.MilvusServiceClient;
import io.milvus.common.clientenum.ConsistencyLevelEnum;
import io.milvus.grpc.DataType;
import io.milvus.grpc.FlushResponse;
import io.milvus.grpc.GetCollectionStatisticsResponse;
import io.milvus.param.*;
import io.milvus.param.collection.*;
import io.milvus.param.index.CreateIndexParam;
import io.milvus.param.index.DescribeIndexParam;
import io.milvus.param.index.DropIndexParam;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Milvus 客户端包装器
 * 负责连接管理、集合生命周期管理
 */
@Slf4j
@Component
public class MilvusClientWrapper {

    private MilvusServiceClient milvusClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${spring.vectorstore.milvus.client.host:localhost}")
    private String host;

    @Value("${spring.vectorstore.milvus.client.port:19530}")
    private int port;

    @Value("${spring.vectorstore.milvus.client.username:}")
    private String username;

    @Value("${spring.vectorstore.milvus.client.password:}")
    private String password;

    @Value("${spring.vectorstore.milvus.collection-name:document_chunks}")
    private String collectionName;

    @Value("${spring.vectorstore.milvus.embedding-dimension:1536}")
    private Integer dimension;

    @Value("${spring.vectorstore.milvus.shards-num:2}")
    private Integer shardsNum;

    @Value("${spring.vectorstore.milvus.collection-description:文档块向量集合}")
    private String collectionDescription;

    @Value("${spring.vectorstore.milvus.index-type:IVF_FLAT}")
    private String indexType;

    @Value("${spring.vectorstore.milvus.metric-type:COSINE}")
    private String metricType;

    @Value("${spring.vectorstore.milvus.index-parameters.nlist:1024}")
    private Integer nlist;

    @Value("${spring.vectorstore.milvus.client.connect-timeout-ms:10000}")
    private int connectionTimeout;

    /**
     * 初始化 Milvus 客户端
     */
    @PostConstruct
    public void init() {
        try {
            log.info("正在连接 Milvus: {}:{}", host, port);

            ConnectParam.Builder connectBuilder = ConnectParam.newBuilder()
                    .withHost(host)
                    .withPort(port)
                    .withConnectTimeout(connectionTimeout, TimeUnit.SECONDS)
                    .withKeepAliveTime(30, TimeUnit.SECONDS)
                    .withKeepAliveTimeout(10, TimeUnit.SECONDS);

            // 如果提供了用户名密码，添加认证信息
            if (username != null && !username.isEmpty() &&
                    password != null && !password.isEmpty()) {
                connectBuilder.withAuthorization(username, password);
                log.info("使用认证连接 Milvus");
            }

            milvusClient = new MilvusServiceClient(connectBuilder.build());

            // 测试连接
            R<Boolean> response = milvusClient.hasCollection(
                    HasCollectionParam.newBuilder()
                            .withCollectionName(collectionName)
                            .build()
            );

            if (response.getStatus() == R.Status.Success.getCode()) {
                log.info("Milvus 连接成功");
            } else {
                log.error("Milvus 连接测试失败: {}", response.getMessage());
                throw new RuntimeException("Milvus 连接失败: " + response.getMessage());
            }

            // 初始化集合
            initCollection();

        } catch (Exception e) {
            log.error("Milvus 初始化失败", e);
            throw new RuntimeException("Milvus 初始化失败: " + e.getMessage(), e);
        }
    }

    /**
     * 初始化集合（如果不存在则创建）
     */
    public void initCollection() {
        if (!hasCollection(collectionName)) {
            log.info("集合不存在，开始创建: {}", collectionName);
            createCollection();
            createIndex();
        } else {
            log.info("集合已存在: {}", collectionName);
            // 检查索引是否存在
            if (!hasIndex()) {
                log.warn("索引不存在，创建索引");
                createIndex();
            }
        }

        // 加载集合到内存
        loadCollection();

        // 获取集合统计信息
        printCollectionStats();
    }

    /**
     * 检查集合是否存在
     */
    public boolean hasCollection(String collectionName) {
        R<Boolean> response = milvusClient.hasCollection(
                HasCollectionParam.newBuilder()
                        .withCollectionName(collectionName)
                        .build()
        );

        if (response.getStatus() == R.Status.Success.getCode()) {
            return response.getData();
        } else {
            log.error("检查集合失败: {}", response.getMessage());
            return false;
        }
    }

    /**
     * 创建集合
     */
    public boolean createCollection() {
        try {
            // 定义主键字段
            FieldType idField = FieldType.newBuilder()
                    .withName("id")
                    .withDataType(DataType.VarChar)
                    .withMaxLength(64)
                    .withPrimaryKey(true)
                    .withAutoID(false)
                    .withDescription("主键ID，全局唯一")
                    .build();

            // 定义向量字段
            FieldType vectorField = FieldType.newBuilder()
                    .withName("vector")
                    .withDataType(DataType.FloatVector)
                    .withDimension(dimension)
                    .withDescription("文本向量")
                    .build();

            // 定义文档ID字段
            FieldType docIdField = FieldType.newBuilder()
                    .withName("doc_id")
                    .withDataType(DataType.Int64)
                    .withDescription("文档ID")
                    .build();

            // 定义内容字段
            FieldType contentField = FieldType.newBuilder()
                    .withName("content")
                    .withDataType(DataType.VarChar)
                    .withMaxLength(65535)
                    .withDescription("文本块内容")
                    .build();

            // 创建集合参数
            CreateCollectionParam createCollectionParam = CreateCollectionParam.newBuilder()
                    .withCollectionName(collectionName)
                    .withDescription(collectionDescription)
                    .withShardsNum(shardsNum)
                    .addFieldType(idField)
                    .addFieldType(vectorField)
                    .addFieldType(docIdField)
                    .addFieldType(contentField)
                    .withConsistencyLevel(ConsistencyLevelEnum.STRONG)
                    .build();

            R<RpcStatus> response = milvusClient.createCollection(createCollectionParam);

            if (response.getStatus() == R.Status.Success.getCode()) {
                log.info("集合创建成功: {}", collectionName);
                return true;
            } else {
                log.error("集合创建失败: {}", response.getMessage());
                return false;
            }
        } catch (Exception e) {
            log.error("创建集合异常", e);
            return false;
        }
    }

    /**
     * 创建索引
     */
    public boolean createIndex() {
        try {
            // 检查字段是否存在
            if (!hasField("vector")) {
                log.error("向量字段不存在，无法创建索引");
                return false;
            }

            // 构建索引参数
            Map<String, Object> extraParams = new HashMap<>();
            extraParams.put("nlist", nlist);
            String extraParamJson = objectMapper.writeValueAsString(extraParams);

            log.info("创建索引参数: {}", extraParamJson);

            CreateIndexParam createIndexParam = CreateIndexParam.newBuilder()
                    .withCollectionName(collectionName)
                    .withFieldName("vector")
                    .withIndexType(IndexType.valueOf(indexType))
                    .withMetricType(MetricType.valueOf(metricType))
                    .withExtraParam(extraParamJson)
                    .withSyncMode(Boolean.TRUE)
                    .withSyncWaitingInterval(500L)
                    .withSyncWaitingTimeout(30L)
                    .build();

            R<RpcStatus> response = milvusClient.createIndex(createIndexParam);

            if (response.getStatus() == R.Status.Success.getCode()) {
                log.info("索引创建成功: 类型={}, 度量={}", indexType, metricType);
                return true;
            } else {
                log.error("索引创建失败: {}", response.getMessage());
                return false;
            }
        } catch (JsonProcessingException e) {
            log.error("索引参数序列化失败", e);
            return false;
        } catch (Exception e) {
            log.error("创建索引异常", e);
            return false;
        }
    }

    /**
     * 检查索引是否存在
     */
    public boolean hasIndex() {
        try {
            DescribeIndexParam describeIndexParam = DescribeIndexParam.newBuilder()
                    .withCollectionName(collectionName)
                    .withFieldName("vector")
                    .build();

            R<io.milvus.grpc.DescribeIndexResponse> response =
                    milvusClient.describeIndex(describeIndexParam);

            return response.getStatus() == R.Status.Success.getCode()
                    && response.getData() != null
                    && response.getData().getIndexDescriptionsCount() > 0;
        } catch (Exception e) {
            log.debug("检查索引失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 删除索引
     */
    public boolean dropIndex() {
        try {
            DropIndexParam dropIndexParam = DropIndexParam.newBuilder()
                    .withCollectionName(collectionName)
                    .withIndexName("vector")
                    .build();

            R<RpcStatus> response = milvusClient.dropIndex(dropIndexParam);

            if (response.getStatus() == R.Status.Success.getCode()) {
                log.info("索引删除成功");
                return true;
            } else {
                log.error("索引删除失败: {}", response.getMessage());
                return false;
            }
        } catch (Exception e) {
            log.error("删除索引异常", e);
            return false;
        }
    }

    /**
     * 加载集合到内存
     */
    public boolean loadCollection() {
        try {
            LoadCollectionParam loadParam = LoadCollectionParam.newBuilder()
                    .withCollectionName(collectionName)
                    .withSyncLoad(true)
                    .withSyncLoadWaitingTimeout(60L)
                    .build();

            R<RpcStatus> response = milvusClient.loadCollection(loadParam);

            if (response.getStatus() == R.Status.Success.getCode()) {
                log.info("集合加载到内存成功");
                return true;
            } else {
                log.error("集合加载失败: {}", response.getMessage());
                return false;
            }
        } catch (Exception e) {
            log.error("加载集合异常", e);
            return false;
        }
    }

    /**
     * 释放集合
     */
    public boolean releaseCollection() {
        try {
            ReleaseCollectionParam releaseParam = ReleaseCollectionParam.newBuilder()
                    .withCollectionName(collectionName)
                    .build();

            R<RpcStatus> response = milvusClient.releaseCollection(releaseParam);

            if (response.getStatus() == R.Status.Success.getCode()) {
                log.info("集合释放成功");
                return true;
            } else {
                log.error("集合释放失败: {}", response.getMessage());
                return false;
            }
        } catch (Exception e) {
            log.error("释放集合异常", e);
            return false;
        }
    }

    /**
     * 删除集合
     */
    public boolean dropCollection() {
        try {
            // 先释放集合
            releaseCollection();

            DropCollectionParam dropParam = DropCollectionParam.newBuilder()
                    .withCollectionName(collectionName)
                    .build();

            R<RpcStatus> response = milvusClient.dropCollection(dropParam);

            if (response.getStatus() == R.Status.Success.getCode()) {
                log.info("集合删除成功");
                return true;
            } else {
                log.error("集合删除失败: {}", response.getMessage());
                return false;
            }
        } catch (Exception e) {
            log.error("删除集合异常", e);
            return false;
        }
    }

    /**
     * 刷新集合（将数据从内存刷到磁盘）
     */
    public boolean flushCollection() {
        try {
            FlushParam flushParam = FlushParam.newBuilder()
                    .addCollectionName(collectionName)
                    .withSyncFlush(true)
                    .withSyncFlushWaitingTimeout(60L)
                    .build();

            R<FlushResponse> response = milvusClient.flush(flushParam);

            if (response.getStatus() == R.Status.Success.getCode()) {
                log.info("集合刷新成功");
                return true;
            } else {
                log.error("集合刷新失败: {}", response.getMessage());
                return false;
            }
        } catch (Exception e) {
            log.error("刷新集合异常", e);
            return false;
        }
    }

    /**
     * 获取集合统计信息
     */
    public Map<String, Object> getCollectionStats() {
        Map<String, Object> stats = new HashMap<>();

        try {
            // 获取行数
            GetCollectionStatisticsParam statParam = GetCollectionStatisticsParam.newBuilder()
                    .withCollectionName(collectionName)
                    .build();

            R<GetCollectionStatisticsResponse> response =
                    milvusClient.getCollectionStatistics(statParam);

            if (response.getStatus() == R.Status.Success.getCode()) {
                List<io.milvus.grpc.KeyValuePair> statsList =
                        response.getData().getStatsList();

                for (io.milvus.grpc.KeyValuePair pair : statsList) {
                    stats.put(pair.getKey(), pair.getValue());
                }
            }

            // 获取集合信息
            DescribeCollectionParam descParam = DescribeCollectionParam.newBuilder()
                    .withCollectionName(collectionName)
                    .build();

            R<io.milvus.grpc.DescribeCollectionResponse> descResponse =
                    milvusClient.describeCollection(descParam);

            if (descResponse.getStatus() == R.Status.Success.getCode()) {
                stats.put("collection_name", descResponse.getData().getCollectionName());
                stats.put("description", descResponse.getData().getSchema().getDescription());
                stats.put("fields_count", descResponse.getData().getSchema().getFieldsCount());
                stats.put("shards_num", descResponse.getData().getShardsNum());
            }

        } catch (Exception e) {
            log.error("获取集合统计信息失败", e);
        }

        return stats;
    }

    /**
     * 打印集合统计信息
     */
    private void printCollectionStats() {
        Map<String, Object> stats = getCollectionStats();
        log.info("========== Milvus 集合统计 ==========");
        stats.forEach((key, value) -> log.info("{}: {}", key, value));
        log.info("=====================================");
    }

    /**
     * 检查字段是否存在
     */
    public boolean hasField(String fieldName) {
        try {
            DescribeCollectionParam descParam = DescribeCollectionParam.newBuilder()
                    .withCollectionName(collectionName)
                    .build();

            R<io.milvus.grpc.DescribeCollectionResponse> response =
                    milvusClient.describeCollection(descParam);

            if (response.getStatus() == R.Status.Success.getCode()) {
                return response.getData().getSchema().getFieldsList().stream()
                        .anyMatch(field -> field.getName().equals(fieldName));
            }
        } catch (Exception e) {
            log.error("检查字段失败", e);
        }
        return false;
    }

    /**
     * 获取 Milvus 客户端
     */
    public MilvusServiceClient getClient() {
        return milvusClient;
    }

    /**
     * 获取集合名称
     */
    public String getCollectionName() {
        return collectionName;
    }

    /**
     * 关闭连接
     */
    @PreDestroy
    public void close() {
        if (milvusClient != null) {
            try {
                log.info("关闭 Milvus 连接");
                milvusClient.close();
            } catch (Exception e) {
                log.error("关闭 Milvus 连接失败", e);
            }
        }
    }
}