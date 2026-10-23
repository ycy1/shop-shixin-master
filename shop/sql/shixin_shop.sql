/*
 Navicat Premium Data Transfer

 Source Server         : 阿里-182.92.85.80
 Source Server Type    : MySQL
 Source Server Version : 80040
 Source Host           : 182.92.85.80:3306
 Source Schema         : shixin_shop

 Target Server Type    : MySQL
 Target Server Version : 80040
 File Encoding         : 65001

 Date: 17/10/2026 20:01:01
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for ai_agents
-- ----------------------------
DROP TABLE IF EXISTS `ai_agents`;
CREATE TABLE `ai_agents`  (
  `id` bigint(0) NOT NULL AUTO_INCREMENT COMMENT '智能体ID',
  `config_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'default' COMMENT '配置类型 default.默认无 database.数据库 document.文档',
  `config_id` bigint(0) NULL DEFAULT NULL COMMENT '配置id',
  `name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '智能体名称',
  `description` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '智能体描述',
  `avatar_url` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '头像URL',
  `user_id` bigint(0) NOT NULL COMMENT '用户ID',
  `tools` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '工具Tool',
  `system_prompt` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '系统提示词',
  `greeting_message` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '欢迎语/开场白',
  `model_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'gpt-4' COMMENT '模型类型: gpt-4, claude-3, qwen等',
  `temperature` decimal(3, 2) NULL DEFAULT 0.70 COMMENT '温度参数',
  `max_tokens` int(0) NULL DEFAULT 4096 COMMENT '最大输出token数',
  `knowledge_base_ids` json NULL COMMENT '关联的知识库ID列表',
  `retrieval_top_k` int(0) NULL DEFAULT 5 COMMENT '检索返回的文档数量',
  `status` tinyint(0) NULL DEFAULT 1 COMMENT '状态: 0-禁用, 1-启用',
  `is_public` tinyint(0) NULL DEFAULT 0 COMMENT '是否公开: 0-私有, 1-公开',
  `created_by` bigint(0) NULL DEFAULT NULL COMMENT '创建者用户ID',
  `total_conversations` int(0) NULL DEFAULT 0 COMMENT '总会话数',
  `total_messages` int(0) NULL DEFAULT 0 COMMENT '总消息数',
  `create_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP(0) COMMENT '创建时间',
  `update_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP(0) ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '修改时间',
  `deleted_at` datetime(0) NULL DEFAULT NULL COMMENT '软删除时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_status`(`status`) USING BTREE,
  INDEX `idx_created_by`(`created_by`) USING BTREE,
  INDEX `idx_create_time`(`create_time`) USING BTREE,
  INDEX `idx_deleted_at`(`deleted_at`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '智能体表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for ai_config_source
-- ----------------------------
DROP TABLE IF EXISTS `ai_config_source`;
CREATE TABLE `ai_config_source`  (
  `id` bigint(0) NOT NULL AUTO_INCREMENT COMMENT '配置ID',
  `source_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '知识源类型: database-数据库, document-文档',
  `source_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '知识源名称',
  `source_description` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT '知识源描述',
  `db_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '数据库类型: mysql, postgresql, oracle',
  `jdbc_url` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT 'JDBC连接URL',
  `db_username` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '数据库用户名',
  `db_password` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '数据库密码（AES加密）',
  `doc_storage_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '文档存储类型: local-本地, url-链接',
  `doc_path` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '文档路径（本地路径或URL链接）',
  `chunk_size` int(0) NULL DEFAULT 500 COMMENT '文档分块大小（字符数）',
  `enabled` tinyint(0) NULL DEFAULT 1 COMMENT '是否启用: 0-禁用, 1-启用',
  `created_by` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP(0) COMMENT '创建时间',
  `updated_by` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '更新人',
  `updated_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP(0) ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '更新时间',
  `deleted` tinyint(0) NULL DEFAULT 0 COMMENT '逻辑删除: 0-未删除, 1-已删除',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_source_name`(`source_name`, `deleted`) USING BTREE,
  INDEX `idx_source_type`(`source_type`) USING BTREE,
  INDEX `idx_enabled`(`enabled`) USING BTREE,
  INDEX `idx_doc_storage_type`(`doc_storage_type`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '知识源配置表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for ai_conversations
-- ----------------------------
DROP TABLE IF EXISTS `ai_conversations`;
CREATE TABLE `ai_conversations`  (
  `id` bigint(0) NOT NULL AUTO_INCREMENT COMMENT '会话ID',
  `agent_id` bigint(0) NOT NULL COMMENT '所属智能体ID',
  `user_id` bigint(0) NOT NULL COMMENT '用户ID',
  `title` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '新对话' COMMENT '会话标题',
  `summary` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '会话摘要',
  `config` json NULL COMMENT '会话级配置: {temperature, max_tokens, system_prompt_override}',
  `context_window` int(0) NULL DEFAULT 10 COMMENT '上下文窗口大小（最近N轮）',
  `total_tokens_used` int(0) NULL DEFAULT 0 COMMENT '累计使用的token数',
  `message_count` int(0) NULL DEFAULT 0 COMMENT '消息总数',
  `status` tinyint(0) NULL DEFAULT 1 COMMENT '状态: 0-已结束, 1-活跃, 2-归档',
  `is_pinned` tinyint(0) NULL DEFAULT 0 COMMENT '是否置顶',
  `last_active_at` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP(0) COMMENT '最后活跃时间',
  `create_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP(0) COMMENT '创建时间',
  `update_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP(0) ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '修改时间',
  `deleted_at` datetime(0) NULL DEFAULT NULL COMMENT '软删除时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_agent_id`(`agent_id`) USING BTREE,
  INDEX `idx_user_id`(`user_id`) USING BTREE,
  INDEX `idx_status`(`status`) USING BTREE,
  INDEX `idx_last_active_at`(`last_active_at`) USING BTREE,
  INDEX `idx_create_time`(`create_time`) USING BTREE,
  INDEX `idx_deleted_at`(`deleted_at`) USING BTREE,
  CONSTRAINT `ai_conversations_ibfk_1` FOREIGN KEY (`agent_id`) REFERENCES `ai_agents` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '会话表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for ai_memories
-- ----------------------------
DROP TABLE IF EXISTS `ai_memories`;
CREATE TABLE `ai_memories`  (
  `id` bigint(0) NOT NULL AUTO_INCREMENT COMMENT '记忆ID',
  `conversation_id` bigint(0) NOT NULL COMMENT '所属会话ID',
  `agent_id` bigint(0) NOT NULL COMMENT '所属智能体ID（冗余字段，便于查询）',
  `user_id` bigint(0) NOT NULL COMMENT '用户ID（冗余字段，便于查询）',
  `role` enum('user','assistant','system','function') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '角色',
  `content` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '消息内容',
  `message_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'text' COMMENT '消息类型: text, image, file, code等',
  `attachments` json NULL COMMENT '附件信息: [{type, url, name}]',
  `function_call` json NULL COMMENT '函数调用信息',
  `function_response` json NULL COMMENT '函数返回结果',
  `parent_message_id` bigint(0) NULL DEFAULT NULL COMMENT '父消息ID（用于构建对话树）',
  `turn_number` int(0) NOT NULL COMMENT '对话轮次编号（从1开始）',
  `tokens_used` int(0) NULL DEFAULT 0 COMMENT '该消息使用的token数',
  `tokens_cumulative` int(0) NULL DEFAULT 0 COMMENT '累计token数（该会话中）',
  `importance_score` decimal(3, 2) NULL DEFAULT 0.50 COMMENT '重要性评分（用于记忆压缩/遗忘）',
  `is_summarized` tinyint(0) NULL DEFAULT 0 COMMENT '是否已被摘要压缩',
  `summary` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '摘要压缩内容',
  `keywords` json NULL COMMENT '提取的关键词列表',
  `entities` json NULL COMMENT '实体识别结果: [{type, value}]',
  `create_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP(0) COMMENT '创建时间',
  `update_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP(0) ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '修改时间',
  `deleted_at` datetime(0) NULL DEFAULT NULL COMMENT '软删除时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_conversation_id`(`conversation_id`) USING BTREE,
  INDEX `idx_agent_id`(`agent_id`) USING BTREE,
  INDEX `idx_user_id`(`user_id`) USING BTREE,
  INDEX `idx_role`(`role`) USING BTREE,
  INDEX `idx_parent_message_id`(`parent_message_id`) USING BTREE,
  INDEX `idx_turn_number`(`turn_number`) USING BTREE,
  INDEX `idx_create_time`(`create_time`) USING BTREE,
  INDEX `idx_is_summarized`(`is_summarized`) USING BTREE,
  INDEX `idx_deleted_at`(`deleted_at`) USING BTREE,
  CONSTRAINT `ai_memories_ibfk_1` FOREIGN KEY (`conversation_id`) REFERENCES `ai_conversations` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '记忆表（消息记录表）' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for chat_msg
-- ----------------------------
DROP TABLE IF EXISTS `chat_msg`;
CREATE TABLE `chat_msg`  (
  `id` bigint(0) NOT NULL AUTO_INCREMENT,
  `chat_id` bigint(0) NULL DEFAULT NULL,
  `sender_id` bigint(0) NOT NULL COMMENT '发送人id',
  `type` enum('text','image') CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT 'text' COMMENT '消息类型',
  `content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT '内容',
  `is_recalled` tinyint(1) NULL DEFAULT 0 COMMENT '是否撤回',
  `create_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP(0) COMMENT '发送时间',
  `ip` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT 'ip地址',
  `location` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT 'ip归属地',
  `file_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '消息是文件时，存的文件名',
  `file_size` double NULL DEFAULT NULL COMMENT '文件大小',
  `duration` int(0) NULL DEFAULT NULL COMMENT '语音时长',
  `reply_id` bigint(0) NULL DEFAULT NULL COMMENT '回复消息id',
  `reply_content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT '回复消息内容',
  `reply_user_id` bigint(0) NULL DEFAULT NULL COMMENT '回复用户id',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '聊天消息' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for file_detail
-- ----------------------------
DROP TABLE IF EXISTS `file_detail`;
CREATE TABLE `file_detail`  (
  `id` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NOT NULL COMMENT '文件id',
  `url` varchar(512) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NOT NULL COMMENT '文件访问地址',
  `size` bigint(0) NULL DEFAULT NULL COMMENT '文件大小，单位字节',
  `filename` varchar(256) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL COMMENT '文件名称',
  `original_filename` varchar(256) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL COMMENT '原始文件名',
  `base_path` varchar(256) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL COMMENT '基础存储路径',
  `path` varchar(256) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL COMMENT '存储路径',
  `ext` varchar(32) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL COMMENT '文件扩展名',
  `content_type` varchar(128) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL COMMENT 'MIME类型',
  `platform` varchar(32) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL COMMENT '存储平台',
  `th_url` varchar(512) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL COMMENT '缩略图访问路径',
  `th_filename` varchar(256) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL COMMENT '缩略图名称',
  `th_size` bigint(0) NULL DEFAULT NULL COMMENT '缩略图大小，单位字节',
  `th_content_type` varchar(128) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL COMMENT '缩略图MIME类型',
  `object_id` varchar(32) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL COMMENT '文件所属对象id',
  `object_type` varchar(32) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL COMMENT '文件所属对象类型，例如用户头像，评价图片',
  `metadata` text CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL COMMENT '文件元数据',
  `user_metadata` text CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL COMMENT '文件用户元数据',
  `th_metadata` text CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL COMMENT '缩略图元数据',
  `th_user_metadata` text CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL COMMENT '缩略图用户元数据',
  `attr` text CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL COMMENT '附加属性',
  `file_acl` varchar(32) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL COMMENT '文件ACL',
  `th_file_acl` varchar(32) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL COMMENT '缩略图文件ACL',
  `hash_info` text CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL COMMENT '哈希信息',
  `upload_id` varchar(128) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL COMMENT '上传ID，仅在手动分片上传时使用',
  `upload_status` int(0) NULL DEFAULT NULL COMMENT '上传状态，仅在手动分片上传时使用，1：初始化完成，2：上传完成',
  `source` varchar(25) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL COMMENT '图片来源',
  `create_time` datetime(0) NULL DEFAULT NULL COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb3 COLLATE = utf8mb3_general_ci COMMENT = '文件记录表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for file_part_detail
-- ----------------------------
DROP TABLE IF EXISTS `file_part_detail`;
CREATE TABLE `file_part_detail`  (
  `id` varchar(32) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NOT NULL COMMENT '分片id',
  `platform` varchar(32) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL COMMENT '存储平台',
  `upload_id` varchar(128) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL COMMENT '上传ID，仅在手动分片上传时使用',
  `e_tag` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL COMMENT '分片 ETag',
  `part_number` int(0) NULL DEFAULT NULL COMMENT '分片号。每一个上传的分片都有一个分片号，一般情况下取值范围是1~10000',
  `part_size` bigint(0) NULL DEFAULT NULL COMMENT '文件大小，单位字节',
  `hash_info` text CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL COMMENT '哈希信息',
  `create_time` datetime(0) NULL DEFAULT NULL COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb3 COLLATE = utf8mb3_general_ci COMMENT = '文件分片信息表，仅在手动分片上传时使用' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for gen_table
-- ----------------------------
DROP TABLE IF EXISTS `gen_table`;
CREATE TABLE `gen_table`  (
  `table_id` bigint(0) NOT NULL AUTO_INCREMENT COMMENT '编号',
  `table_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '' COMMENT '表名称',
  `table_comment` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '' COMMENT '表描述',
  `create_time` datetime(0) NULL DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime(0) NULL DEFAULT NULL COMMENT '修改时间',
  PRIMARY KEY (`table_id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '代码生成业务表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for gen_table_column
-- ----------------------------
DROP TABLE IF EXISTS `gen_table_column`;
CREATE TABLE `gen_table_column`  (
  `column_id` bigint(0) NOT NULL AUTO_INCREMENT COMMENT '编号',
  `table_id` bigint(0) NOT NULL COMMENT '归属表编号',
  `column_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '列名称',
  `column_comment` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '列描述',
  `column_type` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '列类型',
  `java_type` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT 'JAVA类型',
  `java_field` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT 'JAVA字段名',
  `is_pk` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '是否主键（1是）',
  `is_required` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '是否必填（1是）',
  `is_insert` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '是否为插入字段（1是）',
  `is_edit` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '是否编辑字段（1是）',
  `is_list` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '是否列表字段（1是）',
  `is_query` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '是否查询字段（1是）',
  `query_type` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT 'EQ' COMMENT '查询方式（等于、不等于、大于、小于、范围）',
  `html_type` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '显示类型（文本框、文本域、下拉框、复选框、单选框、日期控件）',
  `sort` int(0) NULL DEFAULT NULL COMMENT '排序',
  PRIMARY KEY (`column_id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 24 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '代码生成业务表字段' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for shop_activity
-- ----------------------------
DROP TABLE IF EXISTS `shop_activity`;
CREATE TABLE `shop_activity`  (
  `id` bigint(0) UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `activity_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '广告名称',
  `position_type` tinyint(0) NOT NULL DEFAULT 1 COMMENT '广告位类型：1首页轮播 2首页Banner 3分类页 4详情页 5弹窗 6其他（字典 position_type）',
  `activity_type` tinyint(0) NOT NULL DEFAULT 1 COMMENT '广告类型：1图片 2视频 3文字 4轮播 5弹窗（字典 ad_type）',
  `store_id` bigint(0) UNSIGNED NULL DEFAULT NULL COMMENT '适用门店ID(shop_store.id)，NULL=全平台',
  `title` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '广告标题',
  `image` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '广告图片',
  `video_url` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '视频URL',
  `content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT '广告内容/文字',
  `link_type` tinyint(0) NOT NULL DEFAULT 1 COMMENT '跳转类型：1商品 2分类 3活动 4外链 5小程序页面 6无跳转（字典 link_type）',
  `link_value` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '跳转值：商品ID/分类ID/活动ID/URL',
  `start_time` datetime(0) NULL DEFAULT NULL COMMENT '投放开始时间',
  `end_time` datetime(0) NULL DEFAULT NULL COMMENT '投放结束时间',
  `weight` int(0) NOT NULL DEFAULT 0 COMMENT '权重，数字越大越靠前',
  `status` tinyint(0) NOT NULL DEFAULT 1 COMMENT '状态：1未开始 2投放中 3已结束 4已取消（字典 ad_status）',
  `launch_time` datetime(0) NULL DEFAULT NULL COMMENT '投放时间（状态第一次置为2时服务端补，之后不覆盖）',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '备注',
  `create_by` bigint(0) NULL DEFAULT NULL COMMENT '创建人用户ID（留痕）',
  `update_by` bigint(0) NULL DEFAULT NULL COMMENT '修改人用户ID',
  `create_time` datetime(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0) COMMENT '创建时间',
  `update_time` datetime(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0) ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '修改时间',
  `is_deleted` tinyint(0) NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除',
  `device_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '设备ID',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_position_type`(`position_type`) USING BTREE,
  INDEX `idx_store`(`store_id`) USING BTREE,
  INDEX `idx_status`(`status`) USING BTREE,
  INDEX `idx_time`(`start_time`, `end_time`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 9 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '广告表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for shop_activity_badge
-- ----------------------------
DROP TABLE IF EXISTS `shop_activity_badge`;
CREATE TABLE `shop_activity_badge`  (
  `id` bigint(0) UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `badge_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '徽章名称',
  `badge_type` tinyint(0) NOT NULL DEFAULT 1 COMMENT '徽章类型：1门店 2活动 3签到 4消费（字典 badge_type）',
  `badge_text` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '徽章文字，如\"新品\"',
  `badge_icon` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '徽章图标URL',
  `priority` int(0) NOT NULL DEFAULT 0 COMMENT '优先级，数字越大越优先',
  `target_id` bigint(0) UNSIGNED NULL DEFAULT NULL COMMENT '门店/活动id（类型是 1 门店时填 shop_store.id、2 活动时填 shop_activity.id；3 签到、4 消费没有目标，留 NULL）',
  `reach_count` bigint(0) NULL DEFAULT 0 COMMENT '满足数，在门店购买数/签到天数',
  `points` int(0) NOT NULL DEFAULT 0 COMMENT '可获得积分',
  `coupon_ids` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '可获优惠劵（券模板ID(shop_activity_coupon.id) 逗号分隔，达标时逐个发到这个人手上）',
  `status` tinyint(0) NOT NULL DEFAULT 1 COMMENT '状态：1启用 0禁用（字典 badge_status）',
  `description` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '说明',
  `create_by` bigint(0) NULL DEFAULT NULL COMMENT '创建人用户ID（留痕）',
  `update_by` bigint(0) NULL DEFAULT NULL COMMENT '修改人用户ID',
  `create_time` datetime(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0) COMMENT '创建时间',
  `update_time` datetime(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0) ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '修改时间',
  `is_deleted` tinyint(0) NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_type`(`badge_type`) USING BTREE,
  INDEX `idx_target`(`badge_type`, `target_id`) USING BTREE,
  INDEX `idx_status`(`status`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 4 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '营销徽章定义表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for shop_activity_badge_user
-- ----------------------------
DROP TABLE IF EXISTS `shop_activity_badge_user`;
CREATE TABLE `shop_activity_badge_user`  (
  `id` bigint(0) UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `badge_id` bigint(0) UNSIGNED NOT NULL COMMENT '徽章ID(shop_activity_badge.id)',
  `user_id` bigint(0) UNSIGNED NOT NULL COMMENT '用户ID(sys_user.id)',
  `badge_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '徽章名称（快照）',
  `badge_text` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '徽章文字（快照）',
  `badge_icon` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '徽章图标（快照）',
  `source` tinyint(0) NOT NULL DEFAULT 1 COMMENT '获得来源：1系统发放 2活动获得 3消费达标 4手动发放 5门店发放（字典 badge_source）',
  `source_id` bigint(0) UNSIGNED NULL DEFAULT NULL COMMENT '来源业务ID（活动ID、订单ID 门店id等）',
  `start_time` datetime(0) NULL DEFAULT NULL COMMENT '生效开始时间',
  `end_time` datetime(0) NULL DEFAULT NULL COMMENT '生效结束时间，NULL=永久',
  `status` tinyint(0) NOT NULL DEFAULT 1 COMMENT '状态：1有效 0失效（字典 user_badge_status）',
  `is_show` tinyint(0) NOT NULL DEFAULT 1 COMMENT '是否展示：1展示 0隐藏',
  `sort` int(0) NOT NULL DEFAULT 0 COMMENT '排序',
  `create_by` bigint(0) NULL DEFAULT NULL COMMENT '创建人用户ID（留痕）',
  `update_by` bigint(0) NULL DEFAULT NULL COMMENT '修改人用户ID',
  `create_time` datetime(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0) COMMENT '创建时间',
  `update_time` datetime(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0) ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '修改时间',
  `is_deleted` tinyint(0) NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_badge_user`(`badge_id`, `user_id`) USING BTREE,
  INDEX `idx_user`(`user_id`) USING BTREE,
  INDEX `idx_badge`(`badge_id`) USING BTREE,
  INDEX `idx_user_status`(`user_id`, `status`) USING BTREE,
  INDEX `idx_expire`(`end_time`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 6 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '徽章用户关联表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for shop_activity_click
-- ----------------------------
DROP TABLE IF EXISTS `shop_activity_click`;
CREATE TABLE `shop_activity_click`  (
  `id` bigint(0) UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `activity_id` bigint(0) UNSIGNED NOT NULL COMMENT '广告ID(shop_activity_ad.id)',
  `event_type` tinyint(0) NOT NULL DEFAULT 2 COMMENT '事件类型：1曝光 2点击（字典 ad_event_type）',
  `user_id` bigint(0) NULL DEFAULT NULL COMMENT '用户ID(sys_user.id)，未登录为NULL',
  `ip` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT 'IP地址',
  `ip_location` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL COMMENT 'ip来源',
  `os` varchar(100) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL COMMENT '登录系统',
  `create_by` bigint(0) NULL DEFAULT NULL COMMENT '创建人用户ID（留痕）',
  `update_by` bigint(0) NULL DEFAULT NULL COMMENT '修改人用户ID',
  `create_time` datetime(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0) COMMENT '创建时间',
  `update_time` datetime(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0) ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '修改时间',
  `is_deleted` tinyint(0) NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_activity_event`(`activity_id`, `event_type`) USING BTREE,
  INDEX `idx_user`(`user_id`) USING BTREE,
  INDEX `idx_create_time`(`create_time`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 461 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '广告曝光/点击记录表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for shop_activity_coupon
-- ----------------------------
DROP TABLE IF EXISTS `shop_activity_coupon`;
CREATE TABLE `shop_activity_coupon`  (
  `id` bigint(0) UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `coupon_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '券码，唯一；新增不传则由系统生成（COUPON_+时间戳）。注意这一列没建唯一键，查重在服务层',
  `coupon_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '券名称',
  `coupon_type` tinyint(0) NOT NULL DEFAULT 1 COMMENT '券类型：1满减券 2折扣券 3免邮券 4代金券（字典 coupon_type）',
  `store_id` bigint(0) UNSIGNED NULL DEFAULT NULL COMMENT '适用门店ID(shop_store.id)，NULL=全平台',
  `user_id` bigint(0) UNSIGNED NULL DEFAULT NULL COMMENT '负责的运营(sys_user.id)；只是登记到人，列表不按它收窄',
  `face_value` decimal(10, 2) NOT NULL COMMENT '面额/折扣值：满减券为金额，折扣券为折扣率如0.9',
  `threshold_amount` decimal(10, 2) NOT NULL COMMENT '使用门槛，满多少可用，0=无门槛',
  `max_discount` decimal(10, 2) NULL DEFAULT NULL COMMENT '最高抵扣金额，折扣券用',
  `total_count` int(0) NOT NULL DEFAULT 0 COMMENT '发行总量，0=不限',
  `issued_count` int(0) NOT NULL DEFAULT 0 COMMENT '已发放数量（只有「发放」这一个写入方，表单改不了）',
  `used_count` int(0) NOT NULL DEFAULT 0 COMMENT '已使用数量（这一期没有写入方，等下单用券那条链接上才动）',
  `valid_type` tinyint(0) NOT NULL DEFAULT 1 COMMENT '有效期类型：1固定时间段 2领取后N天（字典 valid_type）',
  `valid_start` datetime(0) NULL DEFAULT NULL COMMENT '有效期开始（valid_type=1时用）',
  `valid_end` datetime(0) NULL DEFAULT NULL COMMENT '有效期结束（valid_type=1时用）',
  `valid_days` int(0) NULL DEFAULT NULL COMMENT '领取后有效天数（valid_type=2时用）',
  `scope_type` tinyint(0) NOT NULL DEFAULT 1 COMMENT '适用范围：1全场 2指定商品 3指定分类 4指定门店（字典 scope_type）',
  `scope_value` json NULL COMMENT '范围值JSON，如{\"productIds\":[1,2],\"categoryIds\":[3]}（由服务层拼，不让手填文本直接进 json 列）',
  `get_way` tinyint(0) NOT NULL DEFAULT 1 COMMENT '领取方式：1主动领取 2系统发放 3活动赠送 4新人礼 5积分兑换（字典 get_way）',
  `status` tinyint(0) NOT NULL DEFAULT 1 COMMENT '状态：1未开始 2发放中 3已结束 4已取消（字典 coupon_status，库默认原来是 0、值域里没这一档所以改成 1）',
  `description` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT '使用说明',
  `create_by` bigint(0) NULL DEFAULT NULL COMMENT '创建人用户ID（留痕）',
  `update_by` bigint(0) NULL DEFAULT NULL COMMENT '修改人用户ID',
  `create_time` datetime(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0) COMMENT '创建时间',
  `update_time` datetime(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0) ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '修改时间',
  `is_deleted` tinyint(0) NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_store`(`store_id`) USING BTREE,
  INDEX `idx_type`(`coupon_type`) USING BTREE,
  INDEX `idx_status`(`status`) USING BTREE,
  INDEX `idx_valid`(`valid_start`, `valid_end`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 3 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '优惠券模板表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for shop_activity_coupon_user
-- ----------------------------
DROP TABLE IF EXISTS `shop_activity_coupon_user`;
CREATE TABLE `shop_activity_coupon_user`  (
  `id` bigint(0) UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `coupon_id` bigint(0) UNSIGNED NOT NULL COMMENT '券模板ID(shop_activity_coupon.id)',
  `coupon_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '券码，唯一（每张用户券一个码，规则见本脚本第 4 条）',
  `user_id` bigint(0) UNSIGNED NOT NULL COMMENT '用户ID(sys_user.id)',
  `order_id` bigint(0) UNSIGNED NULL DEFAULT NULL COMMENT '使用的订单ID',
  `order_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '订单号（冗余）',
  `coupon_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '券名称（快照）',
  `coupon_type` tinyint(0) NOT NULL DEFAULT 1 COMMENT '券类型（快照）',
  `face_value` decimal(10, 2) NOT NULL COMMENT '面额/折扣值（快照）',
  `threshold_amount` decimal(10, 2) NOT NULL COMMENT '使用门槛（快照）',
  `max_discount` decimal(10, 2) NULL DEFAULT NULL COMMENT '最高抵扣（快照）',
  `scope_type` tinyint(0) NOT NULL DEFAULT 1 COMMENT '适用范围（快照）',
  `status` tinyint(0) NOT NULL DEFAULT 0 COMMENT '状态：0未使用 1已使用 2已过期 3已作废（字典 coupon_user_status）',
  `receive_time` datetime(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0) COMMENT '领取时间',
  `use_time` datetime(0) NULL DEFAULT NULL COMMENT '使用时间',
  `expire_time` datetime(0) NOT NULL COMMENT '过期时间（发放时按模板的有效期算：1固定时间段取 valid_end，2领取后N天取 领取时间+valid_days）',
  `get_way` tinyint(0) NOT NULL DEFAULT 1 COMMENT '领取方式：1主动领取 2系统发放 3活动赠送 4新人礼 5积分兑换（字典 get_way；后台这一页的「发放」落的是 2）',
  `remark` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '备注',
  `create_by` bigint(0) NULL DEFAULT NULL COMMENT '创建人用户ID（留痕）',
  `update_by` bigint(0) NULL DEFAULT NULL COMMENT '修改人用户ID',
  `create_time` datetime(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0) COMMENT '创建时间',
  `update_time` datetime(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0) ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '修改时间',
  `is_deleted` tinyint(0) NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_coupon_code`(`coupon_code`) USING BTREE,
  INDEX `idx_user_status`(`user_id`, `status`) USING BTREE,
  INDEX `idx_coupon`(`coupon_id`) USING BTREE,
  INDEX `idx_order`(`order_id`) USING BTREE,
  INDEX `idx_expire`(`expire_time`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 16 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '用户优惠券表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for shop_activity_points
-- ----------------------------
DROP TABLE IF EXISTS `shop_activity_points`;
CREATE TABLE `shop_activity_points`  (
  `id` bigint(0) UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint(0) UNSIGNED NOT NULL COMMENT '用户ID(sys_user.id)，一个用户一行（这些积分是谁的，不是谁建的账）',
  `total_points` int(0) NOT NULL DEFAULT 0 COMMENT '累计获得积分',
  `used_points` int(0) NOT NULL DEFAULT 0 COMMENT '累计消耗积分',
  `balance` int(0) NOT NULL DEFAULT 0 COMMENT '当前可用积分',
  `frozen_points` int(0) NOT NULL DEFAULT 0 COMMENT '冻结积分（下单锁定）',
  `expire_points` int(0) NOT NULL DEFAULT 0 COMMENT '即将过期积分',
  `create_by` bigint(0) NULL DEFAULT NULL COMMENT '创建人用户ID（留痕）',
  `update_by` bigint(0) NULL DEFAULT NULL COMMENT '修改人用户ID',
  `create_time` datetime(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0) COMMENT '创建时间',
  `update_time` datetime(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0) ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '修改时间',
  `is_deleted` tinyint(0) NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_user`(`user_id`) USING BTREE,
  INDEX `idx_balance`(`balance`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 3 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '积分账户表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for shop_activity_points_log
-- ----------------------------
DROP TABLE IF EXISTS `shop_activity_points_log`;
CREATE TABLE `shop_activity_points_log`  (
  `id` bigint(0) UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint(0) UNSIGNED NOT NULL COMMENT '用户ID(sys_user.id)',
  `points` int(0) NOT NULL COMMENT '变动积分，正为获取，负为消耗',
  `balance` int(0) NOT NULL COMMENT '变动后余额',
  `biz_type` tinyint(0) NOT NULL DEFAULT 1 COMMENT '业务类型：1签到 2购物 3评价 4分享 5邀请 6注册 7兑换 8抵扣 9过期 10管理员调整（字典 points_biz_type）',
  `biz_id` bigint(0) UNSIGNED NULL DEFAULT NULL COMMENT '关联业务ID（订单ID、评价ID等）',
  `biz_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '关联业务单号',
  `description` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '说明',
  `expire_time` datetime(0) NULL DEFAULT NULL COMMENT '过期时间（获取时记，过期时用；消耗那几行留 NULL）',
  `create_by` bigint(0) NULL DEFAULT NULL COMMENT '创建人用户ID（留痕）',
  `create_time` datetime(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0) COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_user`(`user_id`) USING BTREE,
  INDEX `idx_user_time`(`user_id`, `create_time`) USING BTREE,
  INDEX `idx_biz`(`biz_type`, `biz_id`) USING BTREE,
  INDEX `idx_expire`(`expire_time`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 38 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '用户积分流水表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for shop_activity_signin
-- ----------------------------
DROP TABLE IF EXISTS `shop_activity_signin`;
CREATE TABLE `shop_activity_signin`  (
  `id` bigint(0) UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint(0) UNSIGNED NOT NULL COMMENT '用户ID(sys_user.id)',
  `sign_date` date NOT NULL COMMENT '签到归属日期（一天一行的判据；补签时这一格是补的那一天，不是执行补签的那天）',
  `sign_time` datetime(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0) COMMENT '签到时间（正常签到=当刻；补签=补的那一刻，归属哪天看 sign_date）',
  `sign_type` tinyint(0) NOT NULL DEFAULT 1 COMMENT '签到类型：1正常签到 2补签（字典 sign_type）',
  `points` int(0) NOT NULL DEFAULT 0 COMMENT '本次获得积分（正常签到和补签都取 sys_config points_config 里 signin 那一档）',
  `reward_json` json NULL COMMENT '额外奖励JSON，如{\"couponId\":1,\"points\":10}（这一轮没有写入方，连续签到奖励还没定）',
  `create_by` bigint(0) UNSIGNED NULL DEFAULT NULL COMMENT '创建人用户ID(sys_user.id)（补签这一行是谁补的）',
  `update_by` bigint(0) UNSIGNED NULL DEFAULT NULL COMMENT '修改人用户ID(sys_user.id)',
  `create_time` datetime(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0) COMMENT '创建时间',
  `update_time` datetime(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0) ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '更新时间',
  `is_deleted` tinyint(0) NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_user_date`(`user_id`, `sign_date`) USING BTREE COMMENT '一人一天一行：正常签到和补签都靠这一条挡重复',
  INDEX `idx_sign_date`(`sign_date`) USING BTREE COMMENT '按天看全平台签到（这一页暂不用，按用户的查询走 uk_user_date 的左前缀）'
) ENGINE = InnoDB AUTO_INCREMENT = 22 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '用户签到记录表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for shop_brand
-- ----------------------------
DROP TABLE IF EXISTS `shop_brand`;
CREATE TABLE `shop_brand`  (
  `id` bigint(0) UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id` bigint(0) UNSIGNED NULL DEFAULT NULL COMMENT '创建人用户ID(sys_user.id)',
  `brand_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '品牌名称',
  `logo` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '品牌LOGO',
  `brand_spokesperson` json NULL COMMENT '代言人图片([{\"name\":\"\",\"url\":\"\"}])',
  `description` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '品牌介绍',
  `contact` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '联系方式',
  `category_id` bigint(0) UNSIGNED NULL DEFAULT NULL COMMENT '所属分类ID',
  `sort` int(0) NOT NULL DEFAULT 0 COMMENT '排序',
  `status` tinyint(0) NOT NULL DEFAULT 1 COMMENT '1启用 0禁用',
  `create_time` datetime(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0) COMMENT '创建时间',
  `update_time` datetime(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0) ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '修改时间',
  `is_deleted` tinyint(0) NOT NULL DEFAULT 0 COMMENT '是否删除 0否 1是',
  `create_by` bigint(0) UNSIGNED NULL DEFAULT NULL COMMENT '创建人用户ID(sys_user.id)',
  `update_by` bigint(0) UNSIGNED NULL DEFAULT NULL COMMENT '修改人用户ID(sys_user.id)',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_brand_name`(`brand_name`) USING BTREE,
  INDEX `idx_status_sort`(`status`, `sort`) USING BTREE,
  INDEX `idx_category`(`category_id`) USING BTREE,
  INDEX `idx_user_id`(`user_id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 10 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '品牌表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for shop_category
-- ----------------------------
DROP TABLE IF EXISTS `shop_category`;
CREATE TABLE `shop_category`  (
  `id` bigint(0) UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id` bigint(0) UNSIGNED NULL DEFAULT NULL COMMENT '创建人用户ID(sys_user.id)',
  `parent_id` bigint(0) UNSIGNED NOT NULL DEFAULT 0 COMMENT '父类目ID，0为根',
  `category_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '类目名称',
  `sort` int(0) NOT NULL DEFAULT 0 COMMENT '排序',
  `level` tinyint(0) NOT NULL DEFAULT 1 COMMENT '层级：1/2/3',
  `icon` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '图标',
  `status` tinyint(0) NOT NULL DEFAULT 1 COMMENT '1启用 0禁用',
  `create_time` datetime(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0),
  `update_time` datetime(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0) ON UPDATE CURRENT_TIMESTAMP(0),
  `create_by` bigint(0) UNSIGNED NULL DEFAULT NULL COMMENT '创建人用户ID(sys_user.id)',
  `update_by` bigint(0) UNSIGNED NULL DEFAULT NULL COMMENT '修改人用户ID(sys_user.id)',
  `is_deleted` tinyint(0) UNSIGNED NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_parent`(`parent_id`) USING BTREE,
  INDEX `idx_user_id`(`user_id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 18 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '商品类目表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for shop_consumer_address
-- ----------------------------
DROP TABLE IF EXISTS `shop_consumer_address`;
CREATE TABLE `shop_consumer_address`  (
  `id` bigint(0) UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint(0) UNSIGNED NOT NULL COMMENT '消费者用户ID(sys_user.id)；不是创建人，创建人留痕看 create_by',
  `receiver_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '收货人姓名',
  `receiver_phone` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '收货人电话',
  `area_code` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '地区编码，逗号分隔的行政区划代码：110000,110100,110101',
  `area_zh` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '地区中文，如 北京市 市辖区 东城区',
  `detail` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '详细地址',
  `postal_code` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '邮编',
  `longitude` decimal(10, 6) NULL DEFAULT NULL COMMENT '经度(GCJ-02)',
  `latitude` decimal(9, 6) NULL DEFAULT NULL COMMENT '纬度(GCJ-02)',
  `tag` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '标签：家/公司/学校（字典 address_tag）',
  `is_default` tinyint(0) NOT NULL DEFAULT 0 COMMENT '是否默认：1是 0否（同一 user_id 下最多一条为 1）',
  `create_by` bigint(0) NULL DEFAULT NULL COMMENT '创建人用户ID',
  `update_by` bigint(0) NULL DEFAULT NULL COMMENT '修改人用户ID',
  `create_time` datetime(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0) COMMENT '创建时间',
  `update_time` datetime(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0) ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '修改时间',
  `is_deleted` tinyint(0) NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_user`(`user_id`) USING BTREE,
  INDEX `idx_user_default`(`user_id`, `is_default`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 6 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '消费者收货地址表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for shop_consumer_browse
-- ----------------------------
DROP TABLE IF EXISTS `shop_consumer_browse`;
CREATE TABLE `shop_consumer_browse`  (
  `id` bigint(0) UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint(0) UNSIGNED NOT NULL COMMENT '浏览的消费者用户ID(sys_user.id)；不是创建人，创建人留痕看 create_by',
  `product_id` bigint(0) UNSIGNED NOT NULL COMMENT '商品ID(shop_product.id)',
  `browse_time` datetime(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0) COMMENT '最近一次浏览时间；重复浏览由服务层刷新这一列，不新增行',
  `create_by` bigint(0) NULL DEFAULT NULL COMMENT '创建人用户ID',
  `update_by` bigint(0) NULL DEFAULT NULL COMMENT '修改人用户ID',
  `create_time` datetime(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0) COMMENT '创建时间（这条足迹第一次记上的时间）',
  `update_time` datetime(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0) ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '修改时间',
  `is_deleted` tinyint(0) NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_user_product`(`user_id`, `product_id`) USING BTREE,
  INDEX `idx_user_time`(`user_id`, `browse_time`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 3 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '浏览足迹表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for shop_consumer_cart
-- ----------------------------
DROP TABLE IF EXISTS `shop_consumer_cart`;
CREATE TABLE `shop_consumer_cart`  (
  `id` bigint(0) UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint(0) UNSIGNED NOT NULL COMMENT '消费者用户ID(sys_user.id)；不是创建人，创建人留痕看 create_by',
  `sku_id` bigint(0) UNSIGNED NOT NULL COMMENT '商品SKU ID(shop_product_sku.id)，无物理外键；加购选的是具体规格',
  `category_id` bigint(0) UNSIGNED NOT NULL COMMENT '商品分类ID(shop_category.id)，加购时从商品冗余',
  `brand_id` bigint(0) UNSIGNED NULL DEFAULT NULL COMMENT '品牌ID(shop_brand.id)，加购时从商品冗余，不关联品牌时为 NULL',
  `store_id` bigint(0) UNSIGNED NULL DEFAULT NULL COMMENT '门店ID(shop_store.id)，NULL=不限定门店',
  `quantity` int(0) NOT NULL DEFAULT 1 COMMENT '数量',
  `selected` tinyint(0) NOT NULL DEFAULT 1 COMMENT '是否选中：1选中 0未选中',
  `create_by` bigint(0) NULL DEFAULT NULL COMMENT '创建人用户ID',
  `update_by` bigint(0) NULL DEFAULT NULL COMMENT '修改人用户ID',
  `create_time` datetime(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0) COMMENT '创建时间（加入购物车时间）',
  `update_time` datetime(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0) ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '修改时间',
  `is_deleted` tinyint(0) NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_user_sku`(`user_id`, `sku_id`) USING BTREE,
  INDEX `idx_user`(`user_id`) USING BTREE,
  INDEX `idx_store`(`store_id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 14 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '购物车表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for shop_consumer_favorite
-- ----------------------------
DROP TABLE IF EXISTS `shop_consumer_favorite`;
CREATE TABLE `shop_consumer_favorite`  (
  `id` bigint(0) UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint(0) UNSIGNED NOT NULL COMMENT '收藏的消费者用户ID(sys_user.id)；不是创建人，创建人留痕看 create_by',
  `target_type` tinyint(0) NOT NULL DEFAULT 1 COMMENT '收藏类型：1商品 2门店 3品牌（字典 favorite_target_type）',
  `target_id` bigint(0) UNSIGNED NOT NULL COMMENT '收藏对象ID：按 target_type 分别指向 shop_product / shop_store / shop_brand',
  `favorite_time` datetime(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0) COMMENT '收藏时间：最近一次收藏上这一对的时间（重新收藏会刷新这一格）',
  `create_by` bigint(0) NULL DEFAULT NULL COMMENT '创建人用户ID',
  `create_time` datetime(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0) COMMENT '创建时间（第一次为这一对建行的时间；列表上的收藏时间看 favorite_time）',
  `update_by` bigint(0) NULL DEFAULT NULL COMMENT '修改人用户ID',
  `update_time` datetime(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0) ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '修改时间',
  `is_deleted` tinyint(0) NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_user_target`(`user_id`, `target_type`, `target_id`) USING BTREE,
  INDEX `idx_target`(`target_type`, `target_id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 7 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '用户收藏表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for shop_consumer_wallet
-- ----------------------------
DROP TABLE IF EXISTS `shop_consumer_wallet`;
CREATE TABLE `shop_consumer_wallet`  (
  `id` bigint(0) UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint(0) UNSIGNED NOT NULL COMMENT '用户ID(sys_user.id)',
  `balance` decimal(14, 2) NOT NULL COMMENT '可用余额',
  `frozen_balance` decimal(14, 2) NOT NULL COMMENT '冻结余额（下单时支付方式为余额时锁定）',
  `total_recharge` decimal(14, 2) NOT NULL COMMENT '累计充值',
  `total_consume` decimal(14, 2) NOT NULL COMMENT '累计消费',
  `total_withdraw` decimal(14, 2) NOT NULL COMMENT '累计提现',
  `gift_balance` decimal(14, 2) NOT NULL COMMENT '赠送余额（充值送的部分）',
  `status` tinyint(0) NOT NULL DEFAULT 1 COMMENT '状态：1正常 0冻结',
  `create_by` bigint(0) NULL DEFAULT NULL COMMENT '创建人用户ID（留痕）',
  `update_by` bigint(0) NULL DEFAULT NULL COMMENT '修改人用户ID',
  `create_time` datetime(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0) COMMENT '创建时间',
  `update_time` datetime(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0) ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '修改时间',
  `is_deleted` tinyint(0) NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_user`(`user_id`) USING BTREE,
  INDEX `idx_status`(`status`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 3 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '用户钱包账户表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for shop_consumer_wallet_log
-- ----------------------------
DROP TABLE IF EXISTS `shop_consumer_wallet_log`;
CREATE TABLE `shop_consumer_wallet_log`  (
  `id` bigint(0) UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint(0) UNSIGNED NOT NULL COMMENT '用户ID(sys_user.id)',
  `amount` decimal(14, 2) NOT NULL COMMENT '变动金额，正为收入，负为支出',
  `balance` decimal(14, 2) NOT NULL COMMENT '变动后余额',
  `biz_type` tinyint(0) NOT NULL DEFAULT 1 COMMENT '业务类型：1充值 2消费 3退款 4提现 5奖励 6冻结 7解冻 8管理员调整（字典 wallet_biz_type）',
  `biz_id` bigint(0) UNSIGNED NULL DEFAULT NULL COMMENT '关联业务ID（订单ID/退款单ID）',
  `biz_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '关联业务单号',
  `description` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '说明',
  `create_by` bigint(0) NULL DEFAULT NULL COMMENT '创建人用户ID（留痕）',
  `update_by` bigint(0) NULL DEFAULT NULL COMMENT '修改人用户ID',
  `create_time` datetime(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0) COMMENT '创建时间',
  `update_time` datetime(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0) ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '修改时间',
  `is_deleted` tinyint(0) NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_user`(`user_id`) USING BTREE,
  INDEX `idx_user_time`(`user_id`, `create_time`) USING BTREE,
  INDEX `idx_biz`(`biz_type`, `biz_id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 10 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '用户钱包流水表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for shop_delivery
-- ----------------------------
DROP TABLE IF EXISTS `shop_delivery`;
CREATE TABLE `shop_delivery`  (
  `id` bigint(0) UNSIGNED NOT NULL AUTO_INCREMENT,
  `delivery_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '配送单号',
  `outbound_id` bigint(0) UNSIGNED NULL DEFAULT NULL COMMENT '关联出库单ID(shop_erp_sale_outbound.id)，一出库单一配送',
  `outbound_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '出库单号（冗余，服务层按 outbound_id 回填）',
  `rider_id` bigint(0) UNSIGNED NULL DEFAULT NULL COMMENT '骑手ID，未接单为NULL',
  `delivery_type` tinyint(0) NOT NULL DEFAULT 1 COMMENT '配送方式：1平台配送 2商家自配 3到店自提 4第三方配送',
  `assign_type` tinyint(0) NULL DEFAULT NULL COMMENT '派单方式：1系统派单 2抢单 3手动指派',
  `receiver_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '收件人姓名',
  `receiver_phone` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '收件人电话',
  `receiver_address` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '收件地址',
  `receiver_lng` decimal(10, 6) NULL DEFAULT NULL COMMENT '收件经度(GCJ-02)',
  `receiver_lat` decimal(9, 6) NULL DEFAULT NULL COMMENT '收件纬度(GCJ-02)',
  `sender_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '发货人',
  `sender_phone` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '发货人电话',
  `sender_address` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '发货地址',
  `sender_lng` decimal(10, 6) NULL DEFAULT NULL COMMENT '发货经度',
  `sender_lat` decimal(9, 6) NULL DEFAULT NULL COMMENT '发货纬度',
  `distance` decimal(8, 2) NULL DEFAULT NULL COMMENT '配送距离(km)',
  `estimated_time` int(0) NULL DEFAULT NULL COMMENT '预计配送时长(分钟)',
  `delivery_fee` decimal(10, 2) NOT NULL COMMENT '配送费',
  `fee_rule_id` bigint(0) UNSIGNED NULL DEFAULT NULL COMMENT '计价规则ID(shop_delivery_fee_rule.id)，NULL=这笔配送费不是规则算的',
  `tip_fee` decimal(10, 2) NOT NULL COMMENT '小费',
  `weight` decimal(8, 2) NULL DEFAULT NULL COMMENT '重量(kg)',
  `expect_time` datetime(0) NULL DEFAULT NULL COMMENT '预计送达时间',
  `assign_time` datetime(0) NULL DEFAULT NULL COMMENT '派单时间',
  `accept_time` datetime(0) NULL DEFAULT NULL COMMENT '接单时间',
  `pickup_time` datetime(0) NULL DEFAULT NULL COMMENT '取货时间',
  `delivered_time` datetime(0) NULL DEFAULT NULL COMMENT '送达时间',
  `cancel_time` datetime(0) NULL DEFAULT NULL COMMENT '取消时间',
  `status` tinyint(0) NOT NULL DEFAULT 0 COMMENT '状态：0待派单 1待接单 2已接单 3配送中 4已送达 5已取消 6异常',
  `cancel_reason` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '取消原因',
  `exception_reason` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '异常原因',
  `create_by` bigint(0) UNSIGNED NULL DEFAULT NULL COMMENT '创建人用户ID(sys_user.id)',
  `update_by` bigint(0) UNSIGNED NULL DEFAULT NULL COMMENT '修改人用户ID(sys_user.id)',
  `create_time` datetime(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0),
  `update_time` datetime(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0) ON UPDATE CURRENT_TIMESTAMP(0),
  `is_deleted` tinyint(0) NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_delivery_no`(`delivery_no`) USING BTREE,
  INDEX `idx_rider`(`rider_id`) USING BTREE,
  INDEX `idx_status`(`status`) USING BTREE,
  INDEX `idx_create_time`(`create_time`) USING BTREE,
  INDEX `idx_outbound_id_no`(`outbound_id`, `outbound_no`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 17 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '配送单表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for shop_delivery_company
-- ----------------------------
DROP TABLE IF EXISTS `shop_delivery_company`;
CREATE TABLE `shop_delivery_company`  (
  `id` bigint(0) UNSIGNED NOT NULL AUTO_INCREMENT,
  `company_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '物流公司编码，唯一；新增时由系统生成（LOGISTICS_+时间戳）',
  `company_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '物流公司名称，唯一',
  `company_short` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '简称，如\"顺丰\"',
  `company_type` tinyint(0) NOT NULL DEFAULT 1 COMMENT '类型：1快递 2物流 3同城配送 4国际（字典 logistics_company_type）',
  `service_phone` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '客服电话',
  `website` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '官网',
  `logo` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT 'LOGO',
  `description` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '简介',
  `support_cod` tinyint(0) NOT NULL DEFAULT 0 COMMENT '是否支持货到付款：1是 0否',
  `support_insurance` tinyint(0) NOT NULL DEFAULT 0 COMMENT '是否支持保价：1是 0否',
  `support_fragile` tinyint(0) NOT NULL DEFAULT 0 COMMENT '是否支持易碎品：1是 0否',
  `max_weight` decimal(8, 2) NULL DEFAULT NULL COMMENT '最大承重(kg)',
  `delivery_area` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '配送范围说明',
  `sort` int(0) NOT NULL DEFAULT 0 COMMENT '排序',
  `status` tinyint(0) NOT NULL DEFAULT 1 COMMENT '状态：1启用 0禁用（字典 logistics_status）',
  `user_id` bigint(0) NULL DEFAULT NULL COMMENT '创建人用户ID(sys_user.id)',
  `create_by` bigint(0) UNSIGNED NULL DEFAULT NULL COMMENT '创建人',
  `update_by` bigint(0) UNSIGNED NULL DEFAULT NULL COMMENT '修改人',
  `create_time` datetime(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0) COMMENT '创建时间',
  `update_time` datetime(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0) ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '更新时间',
  `is_deleted` tinyint(0) NOT NULL DEFAULT 0 COMMENT '逻辑删除：1已删除 0未删除',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_company_code`(`company_code`) USING BTREE,
  UNIQUE INDEX `uk_company_name`(`company_name`) USING BTREE,
  INDEX `idx_company_type`(`company_type`) USING BTREE,
  INDEX `idx_status_sort`(`status`, `sort`) USING BTREE,
  INDEX `idx_user_id`(`user_id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '物流公司表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for shop_delivery_fee_rule
-- ----------------------------
DROP TABLE IF EXISTS `shop_delivery_fee_rule`;
CREATE TABLE `shop_delivery_fee_rule`  (
  `id` bigint(0) UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `rule_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '规则名称（列表和提示语上显示这一条叫什么）',
  `delivery_type` tinyint(0) NOT NULL DEFAULT 1 COMMENT '适用配送类型：1平台配送 2商家自配 3到店自提 4第三方配送（同 shop_delivery.delivery_type / delivery_type 字典）',
  `base_fee` decimal(10, 2) NOT NULL COMMENT '起步配送费（元）',
  `base_distance` decimal(10, 2) NOT NULL COMMENT '起步价包含的公里数，超出部分按每公里费收',
  `per_km_fee` decimal(10, 2) NOT NULL COMMENT '超出起步里程后每公里加收（元/km）',
  `weight_free` decimal(10, 2) NOT NULL COMMENT '免费重量上限（kg），超出部分按每公斤费收',
  `per_kg_fee` decimal(10, 2) NOT NULL COMMENT '超重部分每公斤加收（元/kg）',
  `peak_fee` decimal(10, 2) NOT NULL COMMENT '高峰时段加价（元）',
  `peak_start` time(0) NULL DEFAULT NULL COMMENT '高峰时段开始时刻，与 peak_end 一起构成区间，任一为空则不加价',
  `peak_end` time(0) NULL DEFAULT NULL COMMENT '高峰时段结束时刻',
  `night_fee` decimal(10, 2) NOT NULL COMMENT '夜间时段加价（元）',
  `night_start` time(0) NULL DEFAULT NULL COMMENT '夜间时段开始时刻，与 night_end 一起构成区间，任一为空则不加价',
  `night_end` time(0) NULL DEFAULT NULL COMMENT '夜间时段结束时刻',
  `weather_fee` decimal(10, 2) NOT NULL COMMENT '天气加价（元）；本期生成配送单时不参与计算',
  `free_delivery_amount` decimal(10, 2) NULL DEFAULT NULL COMMENT '订单实付满此金额免配送费（元），NULL 表示不启用这一档',
  `free_delivery_distance` decimal(10, 2) NULL DEFAULT NULL COMMENT '配送距离在此公里数内免配送费，NULL 表示不启用这一档',
  `priority` int(0) NOT NULL DEFAULT 0 COMMENT '优先级，数字越大越优先；同优先级时取 id 最小那条',
  `status` tinyint(0) NOT NULL DEFAULT 1 COMMENT '状态：1启用 0禁用（只有启用的规则参与计算）',
  `user_id` bigint(0) UNSIGNED NOT NULL COMMENT '绑定账号：仅记录是哪家/哪个账号建的，不参与数据范围过滤',
  `create_by` bigint(0) UNSIGNED NULL DEFAULT NULL COMMENT '创建人用户ID',
  `update_by` bigint(0) UNSIGNED NULL DEFAULT NULL COMMENT '修改人用户ID',
  `create_time` datetime(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0) COMMENT '创建时间',
  `update_time` datetime(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0) ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '修改时间',
  `is_deleted` tinyint(0) NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_status_priority`(`status`, `priority`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 3 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '配送费规则表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for shop_delivery_rider
-- ----------------------------
DROP TABLE IF EXISTS `shop_delivery_rider`;
CREATE TABLE `shop_delivery_rider`  (
  `id` bigint(0) UNSIGNED NOT NULL AUTO_INCREMENT,
  `rider_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '骑手编码',
  `user_id` bigint(0) UNSIGNED NULL DEFAULT NULL COMMENT '骑手登录账号ID(sys_user.id)，NULL表示未开通账号',
  `rider_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '骑手姓名',
  `phone` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '手机号',
  `avatar` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '头像',
  `logistics_company_id` bigint(0) NULL DEFAULT NULL COMMENT '所属物流公司ID(shop_delivery_company.id)，NULL为未挂靠',
  `resident_address` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '常驻地点（地址文本，前端 ShopMap 选点带回）',
  `resident_lng` decimal(10, 6) NULL DEFAULT NULL COMMENT '常驻经度(GCJ-02)，NULL=没填，配送列表排不出距离',
  `resident_lat` decimal(10, 6) NULL DEFAULT NULL COMMENT '常驻纬度(GCJ-02)',
  `id_card` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '身份证号',
  `id_card_front` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '身份证正面',
  `id_card_back` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '身份证反面',
  `health_cert` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '健康证图片',
  `health_cert_expire` datetime(0) NULL DEFAULT NULL COMMENT '健康证到期日',
  `vehicle_type` tinyint(0) NOT NULL DEFAULT 1 COMMENT '交通工具：1电动车 2摩托车 3自行车 4汽车',
  `vehicle_no` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '车牌号',
  `service_city` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '服务城市',
  `service_area` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '服务区域，逗号分隔',
  `max_order_count` int(0) NOT NULL DEFAULT 5 COMMENT '最大同时接单数',
  `online_status` tinyint(0) NOT NULL DEFAULT 0 COMMENT '在线状态：1在线 0离线 2忙碌',
  `work_status` tinyint(0) NOT NULL DEFAULT 0 COMMENT '工作状态：1接单中 0休息',
  `status` tinyint(0) NOT NULL DEFAULT 1 COMMENT '启用状态：1启用 0禁用',
  `total_order_count` int(0) NOT NULL DEFAULT 0 COMMENT '累计接单数',
  `total_income` decimal(12, 2) NOT NULL COMMENT '累计收入',
  `rating` decimal(3, 2) NULL DEFAULT NULL COMMENT '评分，如4.85',
  `create_by` bigint(0) UNSIGNED NULL DEFAULT NULL COMMENT '创建人用户ID(sys_user.id)',
  `update_by` bigint(0) UNSIGNED NULL DEFAULT NULL COMMENT '修改人用户ID(sys_user.id)',
  `create_time` datetime(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0),
  `update_time` datetime(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0) ON UPDATE CURRENT_TIMESTAMP(0),
  `is_deleted` tinyint(0) NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_rider_code`(`rider_code`) USING BTREE,
  UNIQUE INDEX `uk_phone`(`phone`) USING BTREE,
  INDEX `idx_online_status`(`online_status`) USING BTREE,
  INDEX `idx_status`(`status`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '门店送货员表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for shop_erp_inventory
-- ----------------------------
DROP TABLE IF EXISTS `shop_erp_inventory`;
CREATE TABLE `shop_erp_inventory`  (
  `id` bigint(0) UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `sku_id` bigint(0) UNSIGNED NOT NULL COMMENT '商品SKU ID(shop_product_sku.id)，无物理外键',
  `warehouse_id` bigint(0) UNSIGNED NOT NULL COMMENT '仓库ID(shop_erp_warehouse.id)，无物理外键',
  `user_id` bigint(0) UNSIGNED NULL DEFAULT NULL COMMENT '最后一次操作这行库存的ERP操作员ID(sys_user.id)；不做数据范围，只记人',
  `stock` int(0) NOT NULL DEFAULT 0 COMMENT '总库存。只有单据动作会写（入库 + 本次入库数），库存页不给手工改',
  `locked_stock` int(0) NOT NULL DEFAULT 0 COMMENT '锁定库存（下单占用）。这一版没有写入方，留 0',
  `in_transit_stock` int(0) NOT NULL DEFAULT 0 COMMENT '在途库存（采购已下单未入库）。这一版没有写入方，留 0',
  `available_stock` int(0) GENERATED ALWAYS AS ((`stock` - `locked_stock`)) STORED COMMENT '可用库存 = 总库存 - 锁定库存；数据库算，任何写入方都不要碰（实体上是 exist=false）' NULL,
  `safety_stock` int(0) NOT NULL DEFAULT 0 COMMENT '安全库存，低于此值预警补货；建行时从 shop_product_sku 快照，之后可按仓库单独改',
  `max_stock` int(0) NULL DEFAULT NULL COMMENT '最大库存，高于此值预警积压；NULL = 不设上限（所以实体上必须带 updateStrategy=IGNORED，否则清空保存没反应）',
  `stock_warn_enable` tinyint(0) NOT NULL DEFAULT 1 COMMENT '是否开启库存预警：1是 0否',
  `cost_price` decimal(10, 2) NULL DEFAULT NULL COMMENT '成本价；入库按加权平均刷新：(原库存×原成本价 + 本次入库数×本次单价) ÷ (原库存 + 本次入库数)',
  `version` int(0) NOT NULL DEFAULT 0 COMMENT '乐观锁版本号；库存增减用 where id=? and version=? 的 CAS，SET 里 version+1',
  `create_by` bigint(0) NULL DEFAULT NULL COMMENT '创建人用户ID',
  `update_by` bigint(0) NULL DEFAULT NULL COMMENT '修改人用户ID',
  `create_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP(0) COMMENT '创建时间',
  `update_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP(0) ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '修改时间',
  `is_deleted` tinyint(0) NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_sku_warehouse`(`sku_id`, `warehouse_id`) USING BTREE,
  INDEX `idx_warehouse`(`warehouse_id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 47 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '商品库存表（商品×仓库 一行）' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for shop_erp_inventory_check
-- ----------------------------
DROP TABLE IF EXISTS `shop_erp_inventory_check`;
CREATE TABLE `shop_erp_inventory_check`  (
  `id` bigint(0) UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `check_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '盘点单号，唯一；新增时不传则由系统生成（CHECK_+时间戳）',
  `warehouse_id` bigint(0) UNSIGNED NOT NULL COMMENT '盘点仓库ID(shop_erp_warehouse.id)',
  `user_id` bigint(0) UNSIGNED NULL DEFAULT NULL COMMENT '负责的ERP操作员(sys_user.id)',
  `check_type` tinyint(0) NOT NULL DEFAULT 1 COMMENT '盘点类型：1全盘 2抽盘 3循环盘点（字典 check_type）',
  `total_sku_count` int(0) NOT NULL DEFAULT 0 COMMENT '盘点SKU总数',
  `diff_sku_count` int(0) NOT NULL DEFAULT 0 COMMENT '差异SKU数',
  `profit_quantity` int(0) NOT NULL DEFAULT 0 COMMENT '盘盈总数量',
  `loss_quantity` int(0) NOT NULL DEFAULT 0 COMMENT '盘亏总数量',
  `profit_amount` decimal(12, 2) NOT NULL COMMENT '盘盈总金额',
  `loss_amount` decimal(12, 2) NOT NULL COMMENT '盘亏总金额',
  `status` tinyint(0) NOT NULL DEFAULT 0 COMMENT '状态：0待审核 1盘点中 2已完成 3已取消（字典 check_status）',
  `start_time` datetime(0) NULL DEFAULT NULL COMMENT '开始时间（状态第一次置为1时服务端补，之后不覆盖）',
  `end_time` datetime(0) NULL DEFAULT NULL COMMENT '结束时间（状态第一次置为2时服务端补，之后不覆盖）',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '备注',
  `create_by` bigint(0) NULL DEFAULT NULL COMMENT '创建人用户ID（留痕）',
  `update_by` bigint(0) NULL DEFAULT NULL COMMENT '修改人用户ID',
  `create_time` datetime(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0) COMMENT '创建时间',
  `update_time` datetime(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0) ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '修改时间',
  `is_deleted` tinyint(0) NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_check_no`(`check_no`) USING BTREE,
  INDEX `idx_warehouse`(`warehouse_id`) USING BTREE,
  INDEX `idx_status`(`status`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '库存盘点单表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for shop_erp_inventory_check_item
-- ----------------------------
DROP TABLE IF EXISTS `shop_erp_inventory_check_item`;
CREATE TABLE `shop_erp_inventory_check_item`  (
  `id` bigint(0) UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `check_id` bigint(0) UNSIGNED NOT NULL COMMENT '盘点单ID(shop_erp_inventory_check.id)',
  `product_id` bigint(0) UNSIGNED NOT NULL COMMENT '商品ID(shop_product.id，SPU 粒度)',
  `sku_id` bigint(0) UNSIGNED NULL DEFAULT NULL COMMENT 'SKU ID(shop_product_sku.id)，无规格为NULL',
  `book_quantity` int(0) NOT NULL DEFAULT 0 COMMENT '账面库存（盘点开始时的系统库存）',
  `actual_quantity` int(0) NULL DEFAULT NULL COMMENT '实盘库存（人工录入）',
  `diff_quantity` int(0) NOT NULL DEFAULT 0 COMMENT '差异数量 = 实盘 - 账面，正为盘盈，负为盘亏',
  `cost_price` decimal(10, 2) NULL DEFAULT NULL COMMENT '成本价（用于算差异金额）',
  `diff_amount` decimal(12, 2) NOT NULL COMMENT '差异金额 = 差异数量 × 成本价',
  `diff_reason` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '差异原因：1正常损耗 2记录错误 3丢失 4其他（字典 diff_reason）',
  `status` tinyint(0) NOT NULL DEFAULT 0 COMMENT '状态：0待盘点 1已盘点 2已调整（字典 check_item_status）',
  `create_by` bigint(0) NULL DEFAULT NULL COMMENT '创建人用户ID（留痕）',
  `update_by` bigint(0) NULL DEFAULT NULL COMMENT '修改人用户ID',
  `create_time` datetime(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0) COMMENT '创建时间',
  `update_time` datetime(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0) ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '修改时间',
  `is_deleted` tinyint(0) NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_check_sku`(`check_id`, `sku_id`) USING BTREE,
  INDEX `idx_check`(`check_id`) USING BTREE,
  INDEX `idx_check_product`(`check_id`, `product_id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 4 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '库存盘点明细表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for shop_erp_inventory_log
-- ----------------------------
DROP TABLE IF EXISTS `shop_erp_inventory_log`;
CREATE TABLE `shop_erp_inventory_log`  (
  `id` bigint(0) UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `sku_id` bigint(0) UNSIGNED NOT NULL COMMENT '商品SKU ID(shop_product_sku.id)，这行流水记的是哪个规格，无物理外键',
  `warehouse_id` bigint(0) UNSIGNED NOT NULL COMMENT '仓库ID(shop_erp_warehouse.id)，无物理外键',
  `user_id` bigint(0) UNSIGNED NULL DEFAULT NULL COMMENT '操作人ID(sys_user.id，account_type=5)；不做数据范围，只记人',
  `change_type` tinyint(0) NOT NULL COMMENT '变动类型：1采购入库 2销售出库 3下单出库 4退货入库 5调拨入库 6调拨出库 7盘盈 8盘亏 9采购退货出库（字典 inventory_change_type）',
  `change_quantity` int(0) NOT NULL COMMENT '变动数量（带符号：入库为正、出库为负）',
  `before_stock` int(0) NOT NULL COMMENT '变动前总库存（shop_erp_inventory.stock 改之前的值）',
  `after_stock` int(0) NOT NULL COMMENT '变动后总库存 = 变动前 + 变动数量',
  `biz_id` bigint(0) UNSIGNED NULL DEFAULT NULL COMMENT '关联业务ID（采购入库那一条是 shop_erp_purchase_in.id），无物理外键',
  `biz_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '关联业务单号（采购入库那一条是入库单号 receipt_no）',
  `remark` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '备注；这一版没有写入方，留空',
  `create_by` bigint(0) NULL DEFAULT NULL COMMENT '创建人用户ID',
  `update_by` bigint(0) NULL DEFAULT NULL COMMENT '修改人用户ID（流水不改，恒等于 create_by）',
  `create_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP(0) COMMENT '创建时间（即这次库存变动发生的时间）',
  `update_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP(0) ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '修改时间（流水不改，恒等于 create_time）',
  `is_deleted` tinyint(0) NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除（没有删除入口，恒 0，只为继承 BaseEntity 必须有这一列）',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_warehouse`(`warehouse_id`) USING BTREE,
  INDEX `idx_biz`(`change_type`, `biz_id`) USING BTREE,
  INDEX `idx_create_time`(`create_time`) USING BTREE,
  INDEX `idx_sku`(`sku_id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 94 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '库存流水表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for shop_erp_inventory_transfer
-- ----------------------------
DROP TABLE IF EXISTS `shop_erp_inventory_transfer`;
CREATE TABLE `shop_erp_inventory_transfer`  (
  `id` bigint(0) UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `transfer_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '调拨单号，唯一；新增时不传则由系统生成（TRANSFER_+时间戳）',
  `from_warehouse_id` bigint(0) UNSIGNED NOT NULL COMMENT '调出仓库ID(shop_erp_warehouse.id)',
  `to_warehouse_id` bigint(0) UNSIGNED NOT NULL COMMENT '调入仓库ID(shop_erp_warehouse.id)',
  `user_id` bigint(0) UNSIGNED NULL DEFAULT NULL COMMENT '负责的ERP操作员(sys_user.id)',
  `total_quantity` int(0) NOT NULL DEFAULT 0 COMMENT '调拨总数量（服务端按明细累加）',
  `total_amount` decimal(12, 2) NOT NULL COMMENT '调拨总成本（服务端按明细累加）',
  `transfer_type` tinyint(0) NOT NULL DEFAULT 1 COMMENT '调拨类型：1普通调拨 2紧急调拨 3门店间调拨（字典 transfer_type）',
  `status` tinyint(0) NOT NULL DEFAULT 0 COMMENT '状态：0待审核 1已审核 2调出中 3已完成 4已取消（字典 transfer_status）',
  `reason` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '调拨原因',
  `audit_time` datetime(0) NULL DEFAULT NULL COMMENT '审核时间（状态第一次置为1时服务端补，之后不覆盖）',
  `out_time` datetime(0) NULL DEFAULT NULL COMMENT '调出时间（状态第一次置为2时服务端补，之后不覆盖）',
  `receive_time` datetime(0) NULL DEFAULT NULL COMMENT '入库时间（状态第一次置为3时服务端补，之后不覆盖）',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '备注',
  `create_by` bigint(0) NULL DEFAULT NULL COMMENT '创建人用户ID（留痕）',
  `update_by` bigint(0) NULL DEFAULT NULL COMMENT '修改人用户ID',
  `create_time` datetime(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0) COMMENT '创建时间',
  `update_time` datetime(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0) ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '修改时间',
  `is_deleted` tinyint(0) NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_transfer_no`(`transfer_no`) USING BTREE,
  INDEX `idx_from`(`from_warehouse_id`) USING BTREE,
  INDEX `idx_to`(`to_warehouse_id`) USING BTREE,
  INDEX `idx_status`(`status`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 5 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '库存调拨单表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for shop_erp_inventory_transfer_item
-- ----------------------------
DROP TABLE IF EXISTS `shop_erp_inventory_transfer_item`;
CREATE TABLE `shop_erp_inventory_transfer_item`  (
  `id` bigint(0) UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `transfer_id` bigint(0) UNSIGNED NOT NULL COMMENT '调拨单ID(shop_erp_inventory_transfer.id)',
  `product_id` bigint(0) UNSIGNED NOT NULL COMMENT '商品ID(shop_product.id，SPU 粒度)',
  `sku_id` bigint(0) UNSIGNED NULL DEFAULT NULL COMMENT 'SKU ID(shop_product_sku.id)，无规格为NULL',
  `quantity` int(0) NOT NULL COMMENT '调拨数量',
  `out_quantity` int(0) NOT NULL DEFAULT 0 COMMENT '已调出数量',
  `receive_quantity` int(0) NOT NULL DEFAULT 0 COMMENT '已入库数量',
  `cost_price` decimal(10, 2) NULL DEFAULT NULL COMMENT '调拨成本价（从调出仓带出）',
  `amount` decimal(12, 2) NOT NULL COMMENT '小计 = 数量 × 成本价',
  `create_by` bigint(0) NULL DEFAULT NULL COMMENT '创建人用户ID（留痕）',
  `update_by` bigint(0) NULL DEFAULT NULL COMMENT '修改人用户ID',
  `create_time` datetime(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0) COMMENT '创建时间',
  `update_time` datetime(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0) ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '修改时间',
  `is_deleted` tinyint(0) NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_transfer`(`transfer_id`) USING BTREE,
  INDEX `idx_transfer_product`(`transfer_id`, `product_id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 6 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '库存调拨明细表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for shop_erp_purchase
-- ----------------------------
DROP TABLE IF EXISTS `shop_erp_purchase`;
CREATE TABLE `shop_erp_purchase`  (
  `id` bigint(0) UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `purchase_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '采购单号，唯一；新增时不传则由系统生成（PURCHASE_+时间戳）',
  `supplier_id` bigint(0) UNSIGNED NOT NULL COMMENT '供应商ID(shop_erp_supplier.id，无物理外键)',
  `store_id` bigint(0) UNSIGNED NULL DEFAULT NULL COMMENT '所属门店(shop_store.id)：表单必填，入库单按它加载可选仓库',
  `user_id` bigint(0) UNSIGNED NULL DEFAULT NULL COMMENT '负责的ERP操作员(sys_user.id，account_type=5)；ERP账号按它认领自己那几条',
  `total_amount` decimal(12, 2) NOT NULL COMMENT '采购总金额（服务端按明细累加）',
  `total_quantity` int(0) NOT NULL DEFAULT 0 COMMENT '采购总数量（服务端按明细累加）',
  `status` tinyint(0) NOT NULL DEFAULT 0 COMMENT '采购状态：0待审核 1已审核 4待入库 2已入库 3已退货（字典 purchase_status；4/2 由入库进度回写、3 由退货出库回写，都不是手工选的）',
  `expect_date` date NULL DEFAULT NULL COMMENT '预计到货日期',
  `audit_time` datetime(0) NULL DEFAULT NULL COMMENT '审核时间（状态第一次置为1时服务端补，之后不覆盖）',
  `receive_time` datetime(0) NULL DEFAULT NULL COMMENT '入库时间（状态第一次置为2时服务端补，之后不覆盖）',
  `contact_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '联系人',
  `contact_phone` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '联系电话',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '备注',
  `create_by` bigint(0) NULL DEFAULT NULL COMMENT '创建人用户ID（留痕）',
  `update_by` bigint(0) NULL DEFAULT NULL COMMENT '修改人用户ID',
  `create_time` datetime(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0) COMMENT '创建时间',
  `update_time` datetime(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0) ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '修改时间',
  `is_deleted` tinyint(0) NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_purchase_no`(`purchase_no`) USING BTREE,
  INDEX `idx_supplier`(`supplier_id`) USING BTREE,
  INDEX `idx_user`(`user_id`) USING BTREE,
  INDEX `idx_status`(`status`) USING BTREE,
  INDEX `idx_store`(`store_id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 10 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '采购单表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for shop_erp_purchase_in
-- ----------------------------
DROP TABLE IF EXISTS `shop_erp_purchase_in`;
CREATE TABLE `shop_erp_purchase_in`  (
  `id` bigint(0) UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `receipt_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '入库单号，唯一；新增时不传则由系统生成（PURCHASE_IN_+时间戳）',
  `inbound_source` tinyint(0) UNSIGNED NOT NULL DEFAULT 2 COMMENT '入库来源：1线上退货 2采购入库 3销货退回 4调拨入库 5盘盈入库 6其他（字典 inbound_source；1 由退款回调建，2 由后台手工建）',
  `purchase_id` bigint(0) UNSIGNED NULL DEFAULT NULL COMMENT '关联采购单ID（shop_erp_purchase.id）：入库来源=2 采购入库 时必填（供应商从它带），其它来源为空',
  `refund_id` bigint(0) UNSIGNED NULL DEFAULT NULL COMMENT '关联退款单ID(sys_refund.id)：入库来源=1 线上退货 时由退款回调写入（重复回调按它拿回原单，不重建），其它来源为 NULL',
  `supplier_id` bigint(0) UNSIGNED NULL DEFAULT NULL COMMENT '供应商ID（来源=2 采购入库 时由服务层按采购单回填，前端传了也不认；没有采购单的来源为 NULL）',
  `warehouse_id` bigint(0) UNSIGNED NOT NULL COMMENT '入库仓库ID(shop_erp_warehouse.id)，无物理外键',
  `total_quantity` int(0) NOT NULL DEFAULT 0 COMMENT '入库总数（服务端按明细累加，前端传了也不认）',
  `total_amount` decimal(12, 2) NOT NULL COMMENT '入库总金额（服务端按明细累加，前端传了也不认）',
  `status` tinyint(0) NOT NULL DEFAULT 0 COMMENT '入库状态：0待入库 1部分入库 2已入库（字典 purchase_in_status）',
  `receive_time` datetime(0) NULL DEFAULT NULL COMMENT '入库时间（状态第一次置为2时服务端补，非表单字段）',
  `user_id` bigint(0) UNSIGNED NULL DEFAULT NULL COMMENT '入库操作人ID(sys_user.id，account_type=5)；表单里那一格直接写这列',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '备注',
  `create_by` bigint(0) NULL DEFAULT NULL COMMENT '创建人用户ID',
  `update_by` bigint(0) NULL DEFAULT NULL COMMENT '修改人用户ID',
  `create_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP(0) COMMENT '创建时间',
  `update_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP(0) ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '修改时间',
  `is_deleted` tinyint(0) NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_receipt_no`(`receipt_no`) USING BTREE,
  INDEX `idx_purchase`(`purchase_id`) USING BTREE,
  INDEX `idx_warehouse`(`warehouse_id`) USING BTREE,
  INDEX `idx_refund_id`(`refund_id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 16 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '采购入库单表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for shop_erp_purchase_in_item
-- ----------------------------
DROP TABLE IF EXISTS `shop_erp_purchase_in_item`;
CREATE TABLE `shop_erp_purchase_in_item`  (
  `id` bigint(0) UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `receipt_id` bigint(0) UNSIGNED NOT NULL COMMENT '入库单ID(shop_erp_purchase_in.id)，无物理外键',
  `sku_id` bigint(0) UNSIGNED NOT NULL COMMENT '商品SKU ID(shop_product_sku.id)，无物理外键',
  `quantity` int(0) NOT NULL COMMENT '入库数量',
  `received_quantity` int(0) NOT NULL DEFAULT 0 COMMENT '这张单已实际入库数量（ quantity，由「入库」动作累加，表单不给填）',
  `price` decimal(10, 2) NOT NULL COMMENT '单价(成本价)，从采购明细带过来，可改',
  `amount` decimal(12, 2) NOT NULL COMMENT '小计（服务端按 数量 × 单价 重算，前端传了也不认）',
  `create_by` bigint(0) NULL DEFAULT NULL COMMENT '创建人用户ID',
  `update_by` bigint(0) NULL DEFAULT NULL COMMENT '修改人用户ID',
  `create_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP(0) COMMENT '创建时间',
  `update_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP(0) ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '修改时间',
  `is_deleted` tinyint(0) NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_receipt`(`receipt_id`) USING BTREE,
  INDEX `idx_sku`(`sku_id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 79 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '采购入库明细表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for shop_erp_purchase_item
-- ----------------------------
DROP TABLE IF EXISTS `shop_erp_purchase_item`;
CREATE TABLE `shop_erp_purchase_item`  (
  `id` bigint(0) UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `purchase_id` bigint(0) UNSIGNED NOT NULL COMMENT '采购单ID(shop_erp_purchase.id)',
  `sku_id` bigint(0) UNSIGNED NOT NULL COMMENT '商品SKU ID(shop_product_sku.id)，无物理外键',
  `quantity` int(0) NOT NULL COMMENT '采购数量',
  `received_quantity` int(0) NOT NULL DEFAULT 0 COMMENT '已入库数量',
  `price` decimal(10, 2) NOT NULL COMMENT '采购单价（不是商品售价，下单时可改，所以不冗余存商品原价）',
  `amount` decimal(12, 2) NOT NULL COMMENT '小计 = 数量 × 单价（服务端算）',
  `create_by` bigint(0) NULL DEFAULT NULL COMMENT '创建人用户ID（留痕）',
  `update_by` bigint(0) NULL DEFAULT NULL COMMENT '修改人用户ID',
  `create_time` datetime(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0) COMMENT '创建时间',
  `update_time` datetime(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0) ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '修改时间',
  `is_deleted` tinyint(0) NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_purchase`(`purchase_id`) USING BTREE,
  INDEX `idx_purchase_sku`(`purchase_id`, `sku_id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 94 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '采购单明细表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for shop_erp_sale
-- ----------------------------
DROP TABLE IF EXISTS `shop_erp_sale`;
CREATE TABLE `shop_erp_sale`  (
  `id` bigint(0) UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `sale_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '销售单号，唯一；新增时不传则由系统生成（SALE_+时间戳）',
  `store_id` bigint(0) UNSIGNED NOT NULL COMMENT '销售门店(shop_store.id)：新建时先选它，明细候选就按「商品属于这家门店」收窄',
  `user_id` bigint(0) UNSIGNED NULL DEFAULT NULL COMMENT '负责的ERP操作员(sys_user.id，account_type=5)；ERP账号按它认领自己那几条',
  `warehouse_id` bigint(0) UNSIGNED NULL DEFAULT NULL COMMENT '发货仓库ID(shop_erp_warehouse.id)，下单时可不填，出库时确定',
  `total_amount` decimal(12, 2) NOT NULL COMMENT '销售总金额（服务端按明细累加）',
  `total_quantity` int(0) NOT NULL DEFAULT 0 COMMENT '销售总数量（服务端按明细累加）',
  `status` tinyint(0) NOT NULL DEFAULT 0 COMMENT '销售状态：0待审核 1已审核 2待出库 3已出库 4已取消（字典 sale_status；2/3 由出库进度回写，不是手工选的）',
  `expect_date` date NULL DEFAULT NULL COMMENT '预计发货日期',
  `audit_time` datetime(0) NULL DEFAULT NULL COMMENT '审核时间（状态第一次置为1时服务端补，之后不覆盖）',
  `ship_time` datetime(0) NULL DEFAULT NULL COMMENT '出库时间（状态第一次置为3时服务端补，之后不覆盖）',
  `contact_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '联系人',
  `contact_phone` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '联系电话',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '备注',
  `create_by` bigint(0) NULL DEFAULT NULL COMMENT '创建人用户ID（留痕）',
  `update_by` bigint(0) NULL DEFAULT NULL COMMENT '修改人用户ID',
  `create_time` datetime(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0) COMMENT '创建时间',
  `update_time` datetime(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0) ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '修改时间',
  `is_deleted` tinyint(0) NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_sale_no`(`sale_no`) USING BTREE,
  INDEX `idx_store`(`store_id`) USING BTREE,
  INDEX `idx_user`(`user_id`) USING BTREE,
  INDEX `idx_warehouse`(`warehouse_id`) USING BTREE,
  INDEX `idx_status`(`status`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 2 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '对外销售单表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for shop_erp_sale_item
-- ----------------------------
DROP TABLE IF EXISTS `shop_erp_sale_item`;
CREATE TABLE `shop_erp_sale_item`  (
  `id` bigint(0) UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `sale_id` bigint(0) UNSIGNED NOT NULL COMMENT '销售单ID(shop_erp_sale.id)',
  `sku_id` bigint(0) UNSIGNED NOT NULL COMMENT '商品SKU ID(shop_product_sku.id)，无物理外键',
  `quantity` int(0) NOT NULL COMMENT '销售数量',
  `shipped_quantity` int(0) NOT NULL DEFAULT 0 COMMENT '已出库数量（由出库单的「出库」动作累加，不能超过 quantity）',
  `price` decimal(10, 2) NOT NULL COMMENT '销售单价（不是商品售价，下单时可改，所以不冗余存商品原价）',
  `amount` decimal(12, 2) NOT NULL COMMENT '小计 = 数量 × 单价（服务端算）',
  `create_by` bigint(0) NULL DEFAULT NULL COMMENT '创建人用户ID（留痕）',
  `update_by` bigint(0) NULL DEFAULT NULL COMMENT '修改人用户ID',
  `create_time` datetime(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0) COMMENT '创建时间',
  `update_time` datetime(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0) ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '修改时间',
  `is_deleted` tinyint(0) NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_sale`(`sale_id`) USING BTREE,
  INDEX `idx_sale_sku`(`sale_id`, `sku_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '对外销售单明细表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for shop_erp_sale_outbound
-- ----------------------------
DROP TABLE IF EXISTS `shop_erp_sale_outbound`;
CREATE TABLE `shop_erp_sale_outbound`  (
  `id` bigint(0) UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '出库单ID（主键，自增）',
  `outbound_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '出库单号（系统生成 SALE_OUTBOUND_+时间戳+3位随机，唯一）',
  `order_id` bigint(0) UNSIGNED NULL DEFAULT NULL COMMENT '来源单据ID，按 outbound_source 决定指向哪张表：1 线上购买 → sys_order.id；2 销售出库 → shop_erp_sale.id；3 采购退货 → shop_erp_purchase.id。联表时必须带上来源条件，不然会串到另一张表的同号单据上；4 调拨出库 / 5 报损出库 / 6 其他 是后台手工建的，没有来源单据，这一列为 NULL',
  `order_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '来源单据单号（建单时冗余）：1 线上购买 = sys_order.order_no；2 销售出库 = shop_erp_sale.sale_no；3 采购退货 = shop_erp_purchase.purchase_no；4 调拨出库 / 5 报损出库 / 6 其他 这三路没有来源单据，为 NULL。列表/详情直接显示这一列，改名前建的历史单会留着当时的号',
  `outbound_source` tinyint(0) NOT NULL DEFAULT 1 COMMENT '出库来源：1线上购买 2销售出库 3采购退货 4调拨出库 5报损出库 6其他（字典 sale_outbound_source；1 由支付回调建，2 由后台手工建）',
  `total_quantity` int(0) NOT NULL DEFAULT 0 COMMENT '计划出库总数（服务端按明细累加，前端传了也不认）',
  `total_amount` decimal(12, 2) NOT NULL COMMENT '出库总金额（服务端按明细 amount 累加，前端传了也不认）',
  `status` tinyint(0) NOT NULL DEFAULT 0 COMMENT '状态：0待出库 1部分出库 2已出库（字典 sale_outbound_status，服务端按明细出库进度算）',
  `outbound_time` datetime(0) NULL DEFAULT NULL COMMENT '出库时间（状态第一次置 2 时服务端补，非表单字段）',
  `user_id` bigint(0) UNSIGNED NULL DEFAULT NULL COMMENT '出库操作人用户ID（sys_user.id，account_type=5）；支付回调自动建单时为 NULL',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '备注',
  `create_by` bigint(0) UNSIGNED NULL DEFAULT NULL COMMENT '创建人用户ID（sys_user.id，自动填充）',
  `update_by` bigint(0) UNSIGNED NULL DEFAULT NULL COMMENT '修改人用户ID（sys_user.id，自动填充）',
  `create_time` datetime(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0) COMMENT '创建时间',
  `update_time` datetime(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0) ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '修改时间',
  `is_deleted` tinyint(0) NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_outbound_no`(`outbound_no`) USING BTREE,
  INDEX `idx_order`(`order_id`) USING BTREE,
  INDEX `idx_user`(`user_id`) USING BTREE,
  INDEX `idx_order_id`(`order_id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 15 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '销售出库单表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for shop_erp_sale_outbound_item
-- ----------------------------
DROP TABLE IF EXISTS `shop_erp_sale_outbound_item`;
CREATE TABLE `shop_erp_sale_outbound_item`  (
  `id` bigint(0) UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '出库明细ID（主键，自增）',
  `outbound_id` bigint(0) UNSIGNED NOT NULL COMMENT '出库单ID（shop_erp_sale_outbound.id）',
  `order_item_id` bigint(0) UNSIGNED NULL DEFAULT NULL COMMENT '来源订单明细ID（shop_order_item.id）：1 线上购买 那一路的行身份，表单不给改；3 采购退货 等没有订单明细的来源为 NULL（那一行按 sku_id + warehouse_id 记）',
  `warehouse_id` bigint(0) UNSIGNED NOT NULL COMMENT '出库仓库ID（shop_erp_warehouse.id，取自订单明细下单时就近分到的那个仓）',
  `sku_id` bigint(0) UNSIGNED NOT NULL COMMENT '商品SKU ID(shop_product_sku.id)，无物理外键',
  `quantity` int(0) NOT NULL COMMENT '计划出库数量（来自订单明细的购买数量）',
  `outbound_quantity` int(0) NOT NULL DEFAULT 0 COMMENT '这张单已实际出库数量（由「出库」动作累加，表单不给填）',
  `cost_price` decimal(10, 2) NULL DEFAULT NULL COMMENT '出库成本价（来源=1 线上订单时由服务端取该仓 shop_erp_inventory.cost_price，表单不可改；其它来源可填）',
  `amount` decimal(12, 2) NOT NULL COMMENT '小计 = 计划出库数量 × 出库成本价（服务端算，前端传了也不认）',
  `create_by` bigint(0) UNSIGNED NULL DEFAULT NULL COMMENT '创建人用户ID（sys_user.id，自动填充）',
  `update_by` bigint(0) UNSIGNED NULL DEFAULT NULL COMMENT '修改人用户ID（sys_user.id，自动填充）',
  `create_time` datetime(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0) COMMENT '创建时间',
  `update_time` datetime(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0) ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '修改时间',
  `is_deleted` tinyint(0) NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_outbound`(`outbound_id`) USING BTREE,
  INDEX `idx_order_item`(`order_item_id`) USING BTREE,
  INDEX `idx_warehouse`(`warehouse_id`) USING BTREE,
  INDEX `idx_sku`(`sku_id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 30 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '销售出库明细表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for shop_erp_supplier
-- ----------------------------
DROP TABLE IF EXISTS `shop_erp_supplier`;
CREATE TABLE `shop_erp_supplier`  (
  `id` bigint(0) UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `supplier_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '供应商编码，唯一；新增时由系统生成（SUPPLIER_+时间戳），表单里禁用',
  `supplier_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '供应商名称',
  `contact_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '联系人',
  `contact_phone` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '联系电话',
  `area_code` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '地区编码：逗号分隔的行政区划代码，如 110000,110100,110101',
  `area_zh` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '地区中文：由 area_code 解析出的冗余快照，列表直接显示这一列',
  `address` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '详细地址（街道门牌，不含省市区）',
  `longitude` decimal(10, 6) NULL DEFAULT NULL COMMENT '经度(GCJ-02)，前端地图选点写入',
  `latitude` decimal(9, 6) NULL DEFAULT NULL COMMENT '纬度(GCJ-02)，前端地图选点写入',
  `bank_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '开户行',
  `bank_account` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '银行账号',
  `tax_no` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '税号',
  `status` tinyint(0) NOT NULL DEFAULT 1 COMMENT '状态：1启用 0禁用（字典 supplier_status）',
  `user_id` bigint(0) NULL DEFAULT NULL COMMENT '创建人用户ID；供应商账号(account_type=4)按它认领自己那条记录',
  `create_by` bigint(0) NULL DEFAULT NULL COMMENT '创建人用户ID（留痕）',
  `update_by` bigint(0) NULL DEFAULT NULL COMMENT '修改人用户ID',
  `create_time` datetime(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0) COMMENT '创建时间',
  `update_time` datetime(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0) ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '修改时间',
  `is_deleted` tinyint(0) NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_supplier_code`(`supplier_code`) USING BTREE,
  INDEX `idx_status`(`status`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 3 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '供应商表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for shop_erp_warehouse
-- ----------------------------
DROP TABLE IF EXISTS `shop_erp_warehouse`;
CREATE TABLE `shop_erp_warehouse`  (
  `id` bigint(0) UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `warehouse_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '仓库编码',
  `warehouse_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '仓库名称',
  `warehouse_type` tinyint(0) NOT NULL DEFAULT 1 COMMENT '仓库类型：1主仓 2分仓 3门店仓（字典 warehouse_type）',
  `store_id` bigint(0) UNSIGNED NULL DEFAULT NULL COMMENT '关联门店(shop_store.id)，门店仓时用；无物理外键',
  `user_id` bigint(0) UNSIGNED NULL DEFAULT NULL COMMENT '负责的ERP操作员ID(sys_user.id，account_type=5)；表单里那一格直接写这列',
  `contact_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '联系人',
  `contact_phone` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '联系电话',
  `area_code` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '地区编码：逗号分隔的行政区划代码，如 110000,110100,110101',
  `area_zh` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '地区中文：由 area_code 解析出的冗余快照，列表直接显示这一列',
  `address` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '详细地址',
  `longitude` decimal(10, 6) NULL DEFAULT NULL COMMENT '经度(GCJ-02)，前端地图选点写入',
  `latitude` decimal(9, 6) NULL DEFAULT NULL COMMENT '纬度(GCJ-02)，前端地图选点写入',
  `capacity` int(0) NULL DEFAULT NULL COMMENT '仓库容量(件)，空表示不限',
  `capacity_warn` int(0) NULL DEFAULT NULL COMMENT '容量预警值(件)，空表示不设预警线',
  `capacity_used` int(0) NOT NULL DEFAULT 0 COMMENT '已用容量(件，冗余)',
  `warn_enable` tinyint(0) NOT NULL DEFAULT 1 COMMENT '是否开启仓库预警：1是 0否',
  `status` tinyint(0) NOT NULL DEFAULT 1 COMMENT '状态：1启用 0禁用（字典 warehouse_status）',
  `create_by` bigint(0) NULL DEFAULT NULL COMMENT '创建人用户ID',
  `update_by` bigint(0) NULL DEFAULT NULL COMMENT '修改人用户ID',
  `create_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP(0) COMMENT '创建时间',
  `update_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP(0) ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '修改时间',
  `is_deleted` tinyint(0) NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_warehouse_code`(`warehouse_code`) USING BTREE,
  INDEX `idx_store`(`store_id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 5 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '仓库表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for shop_icon
-- ----------------------------
DROP TABLE IF EXISTS `shop_icon`;
CREATE TABLE `shop_icon`  (
  `id` bigint(0) UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id` bigint(0) UNSIGNED NULL DEFAULT NULL COMMENT '创建人用户ID(sys_user.id)',
  `icon_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '图标名称',
  `icon_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '图标编码，唯一',
  `icon_type` tinyint(0) NOT NULL DEFAULT 1 COMMENT '1 SVG 2 图片URL 3 字体类名',
  `icon_value` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '图标内容',
  `category_id` bigint(0) UNSIGNED NULL DEFAULT NULL COMMENT '商品分类ID',
  `sort` int(0) NOT NULL DEFAULT 0 COMMENT '排序',
  `status` tinyint(0) NOT NULL DEFAULT 1 COMMENT '1启用 0禁用',
  `remark` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '备注',
  `create_time` datetime(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0),
  `update_time` datetime(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0) ON UPDATE CURRENT_TIMESTAMP(0),
  `is_deleted` tinyint(0) NOT NULL DEFAULT 0 COMMENT '是否删除 0否 1是',
  `create_by` bigint(0) UNSIGNED NULL DEFAULT NULL COMMENT '创建人用户ID(sys_user.id)',
  `update_by` bigint(0) UNSIGNED NULL DEFAULT NULL COMMENT '修改人用户ID(sys_user.id)',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_icon_code`(`icon_code`) USING BTREE,
  INDEX `idx_category`(`category_id`) USING BTREE,
  INDEX `idx_status_sort`(`status`, `sort`) USING BTREE,
  INDEX `idx_user_id`(`user_id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 22 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '图标库表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for shop_order_item
-- ----------------------------
DROP TABLE IF EXISTS `shop_order_item`;
CREATE TABLE `shop_order_item`  (
  `id` bigint(0) UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '明细ID（主键，自增）',
  `order_id` bigint(0) UNSIGNED NOT NULL COMMENT '订单ID（sys_order.id）',
  `user_id` bigint(0) UNSIGNED NOT NULL COMMENT '下单消费者ID（sys_user.id，不是后台创建人）',
  `store_id` bigint(0) UNSIGNED NULL DEFAULT NULL COMMENT '所属门店ID（shop_store.id，下单时从商品冗余），NULL=非门店商品',
  `warehouse_id` bigint(0) UNSIGNED NULL DEFAULT NULL COMMENT '发货仓库ID',
  `order_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '订单号（冗余 sys_order.order_no，便于直接按单号查明细）',
  `sku_id` bigint(0) UNSIGNED NOT NULL COMMENT '商品SKU ID(shop_product_sku.id)，无物理外键',
  `product_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '商品名称（下单时快照，商品改名不回刷）',
  `spec_text` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '规格文本（下单时抄 shop_product_sku.spec_text 的快照），NULL=无规格',
  `image` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '商品主图（下单时快照）',
  `price` decimal(10, 2) NOT NULL COMMENT '成交单价（下单时取自 shop_product_sku.product_price）',
  `quantity` int(0) NOT NULL COMMENT '购买数量',
  `returned_quantity` int(0) UNSIGNED NOT NULL DEFAULT 0 COMMENT '已退货数量（累计）：退款回调成功时按这次退的件数加上；可退数 = quantity - 本列',
  `total_amount` decimal(12, 2) NOT NULL COMMENT '小计（原价口径）= price × quantity，服务端算',
  `discount_amount` decimal(10, 2) NOT NULL COMMENT '这一行的优惠金额',
  `used_points` int(0) NOT NULL DEFAULT 0 COMMENT '这一行下单时使用的积分（服务端算，行实付 = 小计 − 优惠 − 积分抵的钱）',
  `gain_points` int(0) NOT NULL DEFAULT 0 COMMENT '这一行赠送的积分（支付回调送分时按 shop_product_sku.gain_points × 件数落库的快照，退款收回送分读这一格；0=没送）',
  `ship_status` tinyint(0) NOT NULL DEFAULT 0 COMMENT '发货状态：0待发货 3已出库 1已发货 4部分退货 2已退货（字典 order_ship_status；3 由商品出库的出库动作在这一行的货出满时回写，4/2 由退款回调按 returned_quantity 的进度回写）',
  `create_by` bigint(0) UNSIGNED NULL DEFAULT NULL COMMENT '创建人用户ID（sys_user.id，自动填充）',
  `update_by` bigint(0) UNSIGNED NULL DEFAULT NULL COMMENT '修改人用户ID（sys_user.id，自动填充）',
  `create_time` datetime(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0) COMMENT '创建时间（= 下单时间）',
  `update_time` datetime(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0) ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '修改时间',
  `is_deleted` tinyint(0) NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_order`(`order_id`) USING BTREE,
  INDEX `idx_user`(`user_id`) USING BTREE,
  INDEX `idx_sku`(`sku_id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 22 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '订单明细表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for shop_product
-- ----------------------------
DROP TABLE IF EXISTS `shop_product`;
CREATE TABLE `shop_product`  (
  `id` bigint(0) UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id` bigint(0) UNSIGNED NULL DEFAULT NULL COMMENT '创建人用户ID(sys_user.id)',
  `product_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '商品编码(SPU编码)',
  `product_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '商品名称',
  `sub_title` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '副标题/卖点',
  `product_desc` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '商品描述',
  `category_id` bigint(0) UNSIGNED NOT NULL COMMENT '商品分类',
  `brand_id` bigint(0) UNSIGNED NULL DEFAULT NULL COMMENT '品牌ID',
  `store_id` bigint(0) UNSIGNED NULL DEFAULT NULL COMMENT '所属门店id(shop_store.id)，NULL=不限定门店',
  `main_image` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '主图URL',
  `product_specs` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '规格id数组(,分隔)',
  `images` json NULL COMMENT '轮播图URL数组([{\"title\":\"\",\"url\":\"\"}])',
  `sort` int(0) NOT NULL DEFAULT 0 COMMENT '排序',
  `status` tinyint(0) NOT NULL DEFAULT 1 COMMENT '状态：1上架 0下架（原来是 SKU 上的列，上下架是整件商品的事，搬到SPU）',
  `carousel_enable` tinyint(0) NOT NULL DEFAULT 0 COMMENT '是否参与首页轮播位：1参与 0不参与（同样是SPU级，运营逐条打开）',
  `cultural_enable` tinyint(0) NOT NULL DEFAULT 0 COMMENT '是否文创商品：1文创商品（必须挂所属门店） 0普通商品',
  `create_time` datetime(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0),
  `update_time` datetime(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0) ON UPDATE CURRENT_TIMESTAMP(0),
  `is_deleted` tinyint(0) NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除',
  `create_by` bigint(0) UNSIGNED NULL DEFAULT NULL COMMENT '创建人用户ID(sys_user.id)',
  `update_by` bigint(0) UNSIGNED NULL DEFAULT NULL COMMENT '修改人用户ID(sys_user.id)',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_product_code`(`product_code`) USING BTREE,
  INDEX `idx_sort`(`sort`) USING BTREE,
  INDEX `idx_created`(`create_time`) USING BTREE,
  INDEX `idx_category`(`category_id`) USING BTREE,
  INDEX `idx_brand`(`brand_id`) USING BTREE,
  INDEX `idx_user_id`(`user_id`) USING BTREE,
  INDEX `idx_status`(`status`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 21 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '商品主表(SPU)，规格在 shop_product_sku' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for shop_product_sku
-- ----------------------------
DROP TABLE IF EXISTS `shop_product_sku`;
CREATE TABLE `shop_product_sku`  (
  `id` bigint(0) UNSIGNED NOT NULL AUTO_INCREMENT,
  `product_id` bigint(0) UNSIGNED NOT NULL COMMENT '所属商品ID(shop_product.id)，1个SPU下多条SKU，无物理外键',
  `sku_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT 'SKU编码，唯一；新增时不传则由系统生成（SKU_+时间戳）',
  `spec_ids` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '' COMMENT '规格值id数组(,分隔，命中 shop_product_spec.id；写入前按id升序归一化)，空串=这条是无规格的默认SKU',
  `spec_text` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '规格文本快照，服务层按 spec_ids 从规格字典现拼，表单不填（下单/单据明细抄的就是这一串）',
  `sku_image` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '该规格的主图URL；NULL=沿用商品的 main_image',
  `sku_images` json NULL COMMENT '规格轮播图URL数组([{\"title\":\"\",\"url\":\"\"}])，第一张同步写进 sku_image；NULL/空数组=这一档没配图，沿用商品 main_image',
  `detail_images` json NULL COMMENT '商品详情图URL数组([{\"title\":\"\",\"url\":\"\"}])，前台详情页「商品详情」那一块整组铺开；NULL/空数组=这一档没详情图',
  `sort` int(0) NOT NULL DEFAULT 0 COMMENT '该商品内的规格排序',
  `product_price` decimal(10, 2) NULL DEFAULT NULL COMMENT '单价',
  `sales_count` int(0) NOT NULL DEFAULT 0 COMMENT '销量(冗余，支付回调按订单明细件数累加)；商品页那一格按SPU把所有SKU的它加起来',
  `weight` decimal(10, 2) NULL DEFAULT NULL COMMENT '单件重量(kg)，配送单按 Σ(重量×数量) 汇总；NULL=没称重',
  `safety_stock` int(0) NOT NULL DEFAULT 0 COMMENT '安全库存，低于此值预警补货；库存建行时快照这一格',
  `max_stock` int(0) NULL DEFAULT NULL COMMENT '最大库存，高于此值预警积压；NULL=不设上限',
  `stock_warn_enable` tinyint(0) NOT NULL DEFAULT 1 COMMENT '是否开启库存预警：1是 0否',
  `gain_points` int(0) NOT NULL DEFAULT 0 COMMENT '买这件商品可获得的积分（下单后发放），0=这件商品不发积分',
  `exchange_points` int(0) NOT NULL DEFAULT 0 COMMENT '兑换这件商品所需的积分，0=不能用积分兑换',
  `sale_enable` tinyint(0) NOT NULL DEFAULT 1 COMMENT '该规格是否可销售：1可销售 0不可销售（与SPU的上架/下架无关）',
  `create_time` datetime(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0),
  `update_time` datetime(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0) ON UPDATE CURRENT_TIMESTAMP(0),
  `is_deleted` tinyint(0) NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除',
  `create_by` bigint(0) UNSIGNED NULL DEFAULT NULL COMMENT '创建人用户ID(sys_user.id)',
  `update_by` bigint(0) UNSIGNED NULL DEFAULT NULL COMMENT '修改人用户ID(sys_user.id)',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_sku_code`(`sku_code`) USING BTREE,
  INDEX `idx_created`(`create_time`) USING BTREE,
  INDEX `idx_product`(`product_id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 51 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '商品SKU表（与 shop_product 1:N）' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for shop_product_spec
-- ----------------------------
DROP TABLE IF EXISTS `shop_product_spec`;
CREATE TABLE `shop_product_spec`  (
  `id` bigint(0) UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id` bigint(0) UNSIGNED NULL DEFAULT NULL COMMENT '创建人用户ID(sys_user.id)',
  `category_id` bigint(0) UNSIGNED NULL DEFAULT NULL COMMENT '商品分类ID(shop_category.id)；NULL=不限类目，任何分类下的商品都是候选',
  `spec_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '规格名称',
  `spec_value` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '规格值',
  `spec_unit` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '规格单位',
  `status` tinyint(0) NOT NULL DEFAULT 1 COMMENT '1启用 0禁用',
  `create_time` datetime(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0),
  `update_time` datetime(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0) ON UPDATE CURRENT_TIMESTAMP(0),
  `is_deleted` tinyint(0) NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除',
  `create_by` bigint(0) UNSIGNED NULL DEFAULT NULL COMMENT '创建人用户ID(sys_user.id)',
  `update_by` bigint(0) UNSIGNED NULL DEFAULT NULL COMMENT '修改人用户ID(sys_user.id)',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_created`(`create_time`) USING BTREE,
  INDEX `idx_category`(`category_id`) USING BTREE,
  INDEX `idx_user_id`(`user_id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 51 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '商品规格表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for shop_store
-- ----------------------------
DROP TABLE IF EXISTS `shop_store`;
CREATE TABLE `shop_store`  (
  `id` bigint(0) UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id` bigint(0) UNSIGNED NULL DEFAULT NULL COMMENT '创建人用户ID(sys_user.id)',
  `investment_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '来源招商编号，建店时按 investment_id 从库里带出来的冗余快照，只给列表/打印看，不参与关联查询',
  `investment_id` bigint(0) UNSIGNED NULL DEFAULT NULL COMMENT '来源招商线索ID(shop_store_investment.id)，建店时写入，之后不可改；NULL=这家店不是招商来的',
  `store_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '门店编码',
  `store_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '门店名称',
  `store_type` tinyint(0) NOT NULL DEFAULT 1 COMMENT '门店类型：1直营 2加盟 3合作',
  `brand_id` bigint(0) UNSIGNED NULL DEFAULT NULL COMMENT '绑定品牌ID(shop_brand.id)，NULL=不绑品牌',
  `contact_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '联系人',
  `contact_phone` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '联系电话',
  `area_code` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '地区编码',
  `area_zh` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '地区中文',
  `address` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '详细地址',
  `longitude` decimal(10, 6) NULL DEFAULT NULL COMMENT '经度',
  `latitude` decimal(10, 6) NULL DEFAULT NULL COMMENT '纬度',
  `business_hours` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '营业时间，如\"09:00-22:00\"',
  `business_status` tinyint(0) NOT NULL DEFAULT 1 COMMENT '营业状态：1营业中 0休息中 2已打烊',
  `is_delivery` tinyint(0) NOT NULL DEFAULT 0 COMMENT '是否配送：1是 0否',
  `delivery_radius` int(0) NULL DEFAULT NULL COMMENT '配送半径(米)',
  `min_delivery_amount` decimal(10, 2) NULL DEFAULT NULL COMMENT '起送金额',
  `logo` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '门店LOGO',
  `cover_images` json NULL COMMENT '门店图片数组([{\"title\":\"\",\"url\":\"\"}])',
  `manager_id` bigint(0) UNSIGNED NULL DEFAULT NULL COMMENT '店长用户ID',
  `sort` int(0) NOT NULL DEFAULT 0 COMMENT '排序',
  `status` tinyint(0) NOT NULL DEFAULT 1 COMMENT '状态：1启用 0禁用',
  `create_time` datetime(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0),
  `update_time` datetime(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0) ON UPDATE CURRENT_TIMESTAMP(0),
  `is_deleted` tinyint(0) NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除',
  `create_by` bigint(0) UNSIGNED NULL DEFAULT NULL COMMENT '创建人用户ID(sys_user.id)',
  `update_by` bigint(0) UNSIGNED NULL DEFAULT NULL COMMENT '修改人用户ID(sys_user.id)',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_store_code`(`store_code`) USING BTREE,
  INDEX `idx_status_sort`(`status`, `sort`) USING BTREE,
  INDEX `idx_business_status`(`business_status`) USING BTREE,
  INDEX `idx_user_id`(`user_id`) USING BTREE,
  INDEX `idx_brand`(`brand_id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 9 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '门店表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for shop_store_investment
-- ----------------------------
DROP TABLE IF EXISTS `shop_store_investment`;
CREATE TABLE `shop_store_investment`  (
  `id` bigint(0) UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `investment_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '招商编号，唯一；新增时不传则由系统生成（INVEST_+时间戳）',
  `user_id` bigint(0) UNSIGNED NULL DEFAULT NULL COMMENT '负责的招商专员(sys_user.id，account_type=0)',
  `applicant_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '申请人姓名',
  `applicant_phone` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '申请人电话',
  `intent_type` tinyint(0) NOT NULL DEFAULT 1 COMMENT '意向类型：1加盟 2代理 3合作 4直营（字典 invest_intent_type）',
  `contract_file` varchar(200) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL COMMENT '签约文件url(pdf docx)',
  `budget_amount` decimal(14, 2) NULL DEFAULT NULL COMMENT '投资预算',
  `has_experience` tinyint(0) NOT NULL DEFAULT 0 COMMENT '是否有行业经验：1有 0无',
  `source` tinyint(0) NOT NULL DEFAULT 1 COMMENT '线索来源：1官网 2电话 3转介绍 4展会 5广告 6其他（字典 invest_source）',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '备注',
  `status` tinyint(0) NOT NULL DEFAULT 0 COMMENT '状态：0待审核 1已签约 2已取消 3已失效（字典 invest_status）',
  `sign_time` datetime(0) NULL DEFAULT NULL COMMENT '签约时间（状态第一次置为1时服务端补）',
  `expiry_time` datetime(0) NULL DEFAULT NULL COMMENT '过期时间',
  `area_code` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL COMMENT '地区编码',
  `area_zh` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL COMMENT '地区中文',
  `longitude` decimal(10, 6) NULL DEFAULT NULL COMMENT '经度(GCJ-02)，前端地图选点写入',
  `latitude` decimal(10, 6) NULL DEFAULT NULL COMMENT '纬度(GCJ-02)，前端地图选点写入',
  `address` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '详细地址（街道、门牌号），地图选点时由高德逆地理自动回填',
  `create_by` bigint(0) NULL DEFAULT NULL COMMENT '创建人用户ID（留痕）',
  `update_by` bigint(0) NULL DEFAULT NULL COMMENT '修改人用户ID',
  `create_time` datetime(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0) COMMENT '创建时间',
  `update_time` datetime(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0) ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '修改时间',
  `is_deleted` tinyint(0) NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_investment_no`(`investment_no`) USING BTREE,
  INDEX `idx_user`(`user_id`) USING BTREE,
  INDEX `idx_phone`(`applicant_phone`) USING BTREE,
  INDEX `idx_status`(`status`) USING BTREE,
  INDEX `idx_create_time`(`create_time`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '门店招商表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for shop_store_training
-- ----------------------------
DROP TABLE IF EXISTS `shop_store_training`;
CREATE TABLE `shop_store_training`  (
  `id` bigint(0) UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `training_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '培训编号，唯一；新增时不传则由系统生成（TRAIN_+时间戳）',
  `training_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '培训名称',
  `content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT '培训内容简介',
  `training_type` tinyint(0) NOT NULL DEFAULT 1 COMMENT '培训类型：1入职培训 2产品培训 3技能培训 4管理培训 5营销培训 6其他（字典 training_type）',
  `train_mode` tinyint(0) NOT NULL DEFAULT 1 COMMENT '培训方式：1线下 2线上直播 3录播 4混合（字典 train_mode）',
  `store_id` bigint(0) UNSIGNED NULL DEFAULT NULL COMMENT '所属门店ID(shop_store.id)，必填（服务层挡；NULL 只可能是 2026-09-24 之前的历史行）',
  `user_id` bigint(0) UNSIGNED NULL DEFAULT NULL COMMENT '负责的培训讲师(sys_user.id account_type=1)',
  `longitude` decimal(10, 6) NULL DEFAULT NULL COMMENT '经度(GCJ-02)，前端地图选点写入',
  `latitude` decimal(10, 6) NULL DEFAULT NULL COMMENT '纬度(GCJ-02)，前端地图选点写入',
  `address` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '详细地址（街道、门牌号）',
  `train_url` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '线上培训链接',
  `start_time` datetime(0) NULL DEFAULT NULL COMMENT '培训开始时间',
  `end_time` datetime(0) NULL DEFAULT NULL COMMENT '培训结束时间',
  `sign_start` datetime(0) NULL DEFAULT NULL COMMENT '报名开始时间',
  `sign_end` datetime(0) NULL DEFAULT NULL COMMENT '报名截止时间',
  `max_count` int(0) NULL DEFAULT NULL COMMENT '最大参训人数',
  `sign_count` int(0) NOT NULL DEFAULT 0 COMMENT '已报名人数',
  `status` tinyint(0) NOT NULL DEFAULT 0 COMMENT '状态：0待发布 1报名中 2已截止 3进行中 4已结束 5已取消（字典 training_status）',
  `publish_time` datetime(0) NULL DEFAULT NULL COMMENT '发布时间（状态第一次置为1时服务端补，之后不覆盖）',
  `finish_time` datetime(0) NULL DEFAULT NULL COMMENT '结束时间（状态第一次置为4时服务端补，之后不覆盖）',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '备注',
  `create_by` bigint(0) NULL DEFAULT NULL COMMENT '创建人用户ID（留痕）',
  `update_by` bigint(0) NULL DEFAULT NULL COMMENT '修改人用户ID',
  `create_time` datetime(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0) COMMENT '创建时间',
  `update_time` datetime(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0) ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '修改时间',
  `is_deleted` tinyint(0) NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_training_no`(`training_no`) USING BTREE,
  INDEX `idx_store`(`store_id`) USING BTREE,
  INDEX `idx_user`(`user_id`) USING BTREE,
  INDEX `idx_status`(`status`) USING BTREE,
  INDEX `idx_start_time`(`start_time`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '门店培训表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for shop_store_training_member
-- ----------------------------
DROP TABLE IF EXISTS `shop_store_training_member`;
CREATE TABLE `shop_store_training_member`  (
  `id` bigint(0) UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `training_id` bigint(0) UNSIGNED NOT NULL COMMENT '培训ID(shop_store_training.id)',
  `store_id` bigint(0) UNSIGNED NOT NULL COMMENT '所属门店ID(shop_store.id)',
  `user_id` bigint(0) UNSIGNED NOT NULL COMMENT '参训员工ID(sys_user.id)',
  `user_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '参训人姓名（快照）',
  `user_phone` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '参训人电话（快照）',
  `sign_status` tinyint(0) NOT NULL DEFAULT 0 COMMENT '报名状态：1已报名 2已取消',
  `sign_time` datetime(0) NULL DEFAULT NULL COMMENT '报名时间',
  `attend_status` tinyint(0) NOT NULL DEFAULT 0 COMMENT '出勤状态：0未签到 1已签到',
  `attend_time` datetime(0) NULL DEFAULT NULL COMMENT '签到时间',
  `create_by` bigint(0) NULL DEFAULT NULL COMMENT '创建人用户ID（留痕）',
  `update_by` bigint(0) NULL DEFAULT NULL COMMENT '修改人用户ID',
  `create_time` datetime(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0) COMMENT '创建时间',
  `update_time` datetime(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0) ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '修改时间',
  `is_deleted` tinyint(0) NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_training_staff`(`training_id`, `user_id`) USING BTREE,
  INDEX `idx_training`(`training_id`) USING BTREE,
  INDEX `idx_store`(`store_id`) USING BTREE,
  INDEX `idx_staff`(`user_id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 4 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '培训参训人员表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for shop_supplier_product
-- ----------------------------
DROP TABLE IF EXISTS `shop_supplier_product`;
CREATE TABLE `shop_supplier_product`  (
  `id` bigint(0) UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `supplier_id` bigint(0) UNSIGNED NOT NULL COMMENT '供应商ID(shop_erp_supplier.id)，无物理外键',
  `sku_id` bigint(0) UNSIGNED NOT NULL COMMENT '商品SKU ID(shop_product_sku.id)，无物理外键',
  `product_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '商品名称(新增/修改时按SPU回填的冗余快照，只为列表和下拉直接显示，商品改名不回刷；规格文本现联 shop_product_sku 带)',
  `category_id` bigint(0) UNSIGNED NULL DEFAULT NULL COMMENT '该商品所属分类ID(shop_category.id，同样是回填的冗余列)；列表要按分类筛再用得上，不参与采购候选的过滤',
  `supply_price` decimal(10, 2) NULL DEFAULT NULL COMMENT '供货价(采购单价的默认值，可空=没谈定)',
  `supply_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '供应商那边的商品编码(自己目录里的货号，不是 shop_product.product_code)',
  `min_order_qty` int(0) NOT NULL DEFAULT 1 COMMENT '最小起订量，必须大于 0',
  `lead_time` int(0) NULL DEFAULT NULL COMMENT '供货周期(天)，从下单到到货',
  `is_default` tinyint(0) NOT NULL DEFAULT 0 COMMENT '是否该商品的默认供应商：1是 0否；同一商品下最多一行是 1，由服务层保证互斥',
  `status` tinyint(0) NOT NULL DEFAULT 1 COMMENT '合作状态：1合作中 0已停止（字典 supplier_product_status）；采购明细的候选只认 1',
  `create_by` bigint(0) NULL DEFAULT NULL COMMENT '创建人用户ID',
  `update_by` bigint(0) NULL DEFAULT NULL COMMENT '修改人用户ID',
  `create_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP(0) COMMENT '创建时间',
  `update_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP(0) ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '修改时间',
  `is_deleted` tinyint(0) NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_supplier_sku`(`supplier_id`, `sku_id`) USING BTREE,
  INDEX `idx_sku`(`sku_id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 41 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '供应商商品(供货关系)' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for sys_comment
-- ----------------------------
DROP TABLE IF EXISTS `sys_comment`;
CREATE TABLE `sys_comment`  (
  `id` int(0) NOT NULL AUTO_INCREMENT COMMENT '评论主键ID，自增唯一标识',
  `business_id` int(0) NOT NULL COMMENT '关联的业务ID（文章/动态ID），表明该评论所属的业务',
  `user_id` int(0) NOT NULL COMMENT '发表评论的用户ID',
  `reply_user_id` int(0) NULL DEFAULT NULL COMMENT '回复人id',
  `parent_id` int(0) NULL DEFAULT NULL COMMENT '父评论ID，用于实现回复评论的层级结构，若为顶级评论则为NULL',
  `comment_type` tinyint(1) NULL DEFAULT 1 COMMENT '评论类型 1-文章 2-动态',
  `content` mediumtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '评论内容，使用utf8mb4字符集以支持更多字符类型',
  `like_count` int(0) NULL DEFAULT 0 COMMENT '点赞数，记录该评论获得的点赞数量',
  `is_stick` int(0) NULL DEFAULT 0 COMMENT '是否置顶',
  `ip` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT 'ip',
  `browser` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '浏览器',
  `system_os` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '系统',
  `ip_source` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT 'ip来源',
  `create_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP(0) COMMENT '创建时间',
  `update_time` datetime(0) NULL DEFAULT NULL COMMENT '修改时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_business_id`(`business_id`) USING BTREE,
  INDEX `idx_user_id`(`user_id`) USING BTREE,
  INDEX `idx_parent_id`(`parent_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '评论' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for sys_comment_like
-- ----------------------------
DROP TABLE IF EXISTS `sys_comment_like`;
CREATE TABLE `sys_comment_like`  (
  `id` bigint(0) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `comment_id` int(0) NOT NULL COMMENT '评论ID',
  `user_id` int(0) NOT NULL COMMENT '用户ID',
  `comment_type` tinyint(1) NULL DEFAULT 1 COMMENT '评论类型 1-文章 2-动态',
  `create_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP(0) COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_comment_id`(`comment_id`) USING BTREE,
  INDEX `idx_user_id`(`user_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '评论点赞' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for sys_config
-- ----------------------------
DROP TABLE IF EXISTS `sys_config`;
CREATE TABLE `sys_config`  (
  `id` bigint(0) NOT NULL AUTO_INCREMENT COMMENT '参数主键',
  `config_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '' COMMENT '参数名称',
  `config_key` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '' COMMENT '参数键名',
  `config_value` varchar(5000) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '' COMMENT '参数键值',
  `config_type` varchar(4) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT 'N' COMMENT '系统内置（Y是 N否）',
  `create_time` datetime(0) NULL DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime(0) NULL DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 4 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '参数配置表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for sys_dept
-- ----------------------------
DROP TABLE IF EXISTS `sys_dept`;
CREATE TABLE `sys_dept`  (
  `id` bigint(0) NOT NULL AUTO_INCREMENT COMMENT '部门ID',
  `parent_id` bigint(0) NULL DEFAULT 0 COMMENT '父部门ID（0表示顶级部门）',
  `name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '部门名称',
  `code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '部门编码（可选，用于对接外部系统）',
  `leader_id` bigint(0) NULL DEFAULT NULL COMMENT '部门负责人ID（关联用户表）',
  `status` tinyint(1) NOT NULL DEFAULT 1 COMMENT '状态：1-启用 0-停用',
  `sort_order` int(0) NULL DEFAULT 0 COMMENT '同级排序权重',
  `create_time` datetime(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0),
  `update_time` datetime(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0) ON UPDATE CURRENT_TIMESTAMP(0),
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_parent_id`(`parent_id`) USING BTREE,
  INDEX `idx_status`(`status`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 8 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '部门表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for sys_dept_user
-- ----------------------------
DROP TABLE IF EXISTS `sys_dept_user`;
CREATE TABLE `sys_dept_user`  (
  `id` bigint(0) NOT NULL AUTO_INCREMENT,
  `user_id` bigint(0) NOT NULL COMMENT '用户ID',
  `dept_id` bigint(0) NOT NULL COMMENT '部门ID',
  `is_primary` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否主部门：1-是 0-否（用于发送消息时取主部门）',
  `position` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '在该部门的职位（可选）',
  `create_time` datetime(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0),
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_user_dept`(`user_id`, `dept_id`) USING BTREE,
  INDEX `idx_dept_id`(`dept_id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 20 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '用户部门关联表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for sys_dict
-- ----------------------------
DROP TABLE IF EXISTS `sys_dict`;
CREATE TABLE `sys_dict`  (
  `id` bigint(0) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '字典名称',
  `type` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '字典类型',
  `status` int(0) NOT NULL DEFAULT 1 COMMENT '是否发布(1:是，0:否)',
  `remark` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '备注',
  `create_time` datetime(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0) COMMENT '创建时间',
  `update_time` datetime(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0) COMMENT '修改时间',
  `sort` int(0) NULL DEFAULT 0 COMMENT '排序',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 117 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '字典表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for sys_dict_data
-- ----------------------------
DROP TABLE IF EXISTS `sys_dict_data`;
CREATE TABLE `sys_dict_data`  (
  `id` bigint(0) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `dict_id` bigint(0) NOT NULL COMMENT '字典类型id',
  `label` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '字典标签',
  `value` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '字典键值',
  `style` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '回显样式',
  `is_default` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '0' COMMENT '是否默认（1是 0否）',
  `sort` int(0) NULL DEFAULT NULL COMMENT '排序',
  `remark` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '备注',
  `status` int(0) NULL DEFAULT 1,
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 419 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '字典数据表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for sys_file_center
-- ----------------------------
DROP TABLE IF EXISTS `sys_file_center`;
CREATE TABLE `sys_file_center`  (
  `id` bigint(0) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `file_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '关联文件id',
  `user_id` bigint(0) NOT NULL COMMENT '发起下载的用户ID (用于权限校验)',
  `business_type` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'none' COMMENT '业务类型',
  `file_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '最终生成的文件名称 (含后缀)',
  `file_url` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '本地相对路径 或 OSS的Key',
  `file_size` bigint(0) NULL DEFAULT 0 COMMENT '文件大小 (单位: Byte)',
  `file_source` tinyint(0) NOT NULL DEFAULT 0 COMMENT '文件来源: 1-用户上传(我的文件), 2-部门, 3-角色, 4-回收站',
  `original_source` tinyint(0) NULL DEFAULT NULL COMMENT '文件原来源',
  `file_status` tinyint(0) NOT NULL DEFAULT 0 COMMENT '文件状态: 1-处理中, 2-已完成(成功), 3-失败, 4-已过期/已清理',
  `fail_reason` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '失败时的错误堆栈信息(用户可读)',
  `create_time` datetime(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0) COMMENT '创建时间',
  `update_time` datetime(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0) ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '最近更新时间',
  `expire_time` datetime(0) NULL DEFAULT NULL COMMENT '文件过期时间',
  `download_count` int(0) NULL DEFAULT 0 COMMENT '该文件被下载的次数',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_file_id`(`file_id`) USING BTREE,
  INDEX `idx_user_id`(`user_id`) USING BTREE,
  INDEX `idx_create_time`(`create_time`) USING BTREE,
  INDEX `idx_status_expire`(`file_status`, `expire_time`) USING BTREE COMMENT '用于定时任务清理过期文件'
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '文件中心记录表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for sys_file_oss
-- ----------------------------
DROP TABLE IF EXISTS `sys_file_oss`;
CREATE TABLE `sys_file_oss`  (
  `id` bigint(0) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `domain` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '访问域名',
  `access_key` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT 'access-key',
  `secret_key` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT 'secret-key',
  `bucket` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '空间名',
  `server_addr` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '（IP:PORT），多个用英文逗号隔开',
  `base_path` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '存储基础路径',
  `platform` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '存储类型',
  `is_enable` int(0) NULL DEFAULT NULL COMMENT '是否启用存储',
  `storage_path` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '本地存储路径',
  `enable_access` int(0) NULL DEFAULT NULL COMMENT '本地启用访问',
  `path_patterns` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '本地访问路径',
  `region` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '仓库所在地域',
  `create_time` datetime(0) NULL DEFAULT NULL COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 3 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '存储平台' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for sys_job
-- ----------------------------
DROP TABLE IF EXISTS `sys_job`;
CREATE TABLE `sys_job`  (
  `job_id` bigint(0) NOT NULL AUTO_INCREMENT COMMENT '任务ID',
  `job_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '任务名称',
  `job_group` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '任务组名',
  `invoke_target` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '调用目标字符串',
  `cron_expression` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT 'cron执行表达式',
  `misfire_policy` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '3' COMMENT '计划执行错误策略（1立即执行 2执行一次 3放弃执行）',
  `concurrent` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '1' COMMENT '是否并发执行（0允许 1禁止）',
  `status` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '0' COMMENT '状态（0正常 1暂停）',
  `create_time` datetime(0) NULL DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime(0) NULL DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '备注信息',
  PRIMARY KEY (`job_id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 5 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '定时任务调度表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for sys_job_log
-- ----------------------------
DROP TABLE IF EXISTS `sys_job_log`;
CREATE TABLE `sys_job_log`  (
  `id` bigint(0) NOT NULL AUTO_INCREMENT COMMENT '任务日志ID',
  `job_id` bigint(0) NOT NULL COMMENT '任务ID',
  `job_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '任务名称',
  `job_group` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '任务组名',
  `invoke_target` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '调用目标字符串',
  `job_message` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '日志信息',
  `status` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '0' COMMENT '执行状态（0正常 1失败）',
  `exception_info` varchar(2000) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '' COMMENT '异常信息',
  `create_time` datetime(0) NULL DEFAULT NULL COMMENT '创建时间',
  `start_time` datetime(0) NULL DEFAULT NULL COMMENT '开始时间',
  `stop_time` datetime(0) NULL DEFAULT NULL COMMENT '结束时间',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 12 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '定时任务调度日志表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for sys_menu
-- ----------------------------
DROP TABLE IF EXISTS `sys_menu`;
CREATE TABLE `sys_menu`  (
  `id` int(0) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `parent_id` varchar(32) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL COMMENT '上级资源ID',
  `path` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL COMMENT '路由路径',
  `component` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL COMMENT '组件路径',
  `title` varchar(100) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL COMMENT '菜单名称',
  `sort` int(0) NULL DEFAULT 0 COMMENT '排序',
  `icon` varchar(32) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL COMMENT '资源图标',
  `type` varchar(32) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL COMMENT '类型 menu、button',
  `create_time` datetime(0) NULL DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime(0) NULL DEFAULT NULL COMMENT '更新时间',
  `redirect` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL COMMENT '重定向地址',
  `name` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL COMMENT '跳转地址',
  `hidden` int(0) NULL DEFAULT NULL COMMENT '是否隐藏',
  `perm` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL COMMENT '权限标识',
  `is_external` int(0) NULL DEFAULT 0 COMMENT '是否外链 0:否  1:是',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 375 CHARACTER SET = utf8mb3 COLLATE = utf8mb3_general_ci COMMENT = '权限资源表 ' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for sys_message
-- ----------------------------
DROP TABLE IF EXISTS `sys_message`;
CREATE TABLE `sys_message`  (
  `id` int(0) NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `nickname` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '昵称',
  `avatar` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '头像',
  `content` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '内容',
  `ip` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT 'IP地址',
  `source` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT 'IP来源',
  `browser` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '浏览器',
  `create_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP(0) COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 2 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '留言' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for sys_notice
-- ----------------------------
DROP TABLE IF EXISTS `sys_notice`;
CREATE TABLE `sys_notice`  (
  `id` bigint(0) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `title` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '标题',
  `content` mediumtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT '公告内容',
  `notice_push` json NULL COMMENT '发送对象',
  `is_show` int(0) NULL DEFAULT NULL COMMENT '是否展示',
  `position` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '显示位置 （top：顶部，right:右侧）',
  `create_time` datetime(0) NULL DEFAULT NULL COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 6 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '公告' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for sys_notifications
-- ----------------------------
DROP TABLE IF EXISTS `sys_notifications`;
CREATE TABLE `sys_notifications`  (
  `id` bigint(0) NOT NULL AUTO_INCREMENT COMMENT '通知的唯一标识，自增主键',
  `send_code` varchar(65) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '发送标识（用于再次发送）',
  `notice_push` json NULL COMMENT '推送对象\r\n{\r\n	\"user\":[],\r\n	\"dept\":[],\r\n	\"role\":[]\r\n}',
  `from_user_id` bigint(0) NULL DEFAULT NULL COMMENT '来自用户id,如评论就存评论的用户id',
  `business_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '通知的类型，如 system、comment、like 等',
  `title` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '通知的标题',
  `message` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '通知的具体内容',
  `business_id` bigint(0) NULL DEFAULT NULL COMMENT '文章id',
  `link` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '跳转链接',
  `create_time` datetime(0) NOT NULL COMMENT '通知的创建时间',
  `del_flag` tinyint(0) NULL DEFAULT 0 COMMENT '删除标记 0 未删除 1 已删除',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 62 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '消息通知表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for sys_notifications_receiver
-- ----------------------------
DROP TABLE IF EXISTS `sys_notifications_receiver`;
CREATE TABLE `sys_notifications_receiver`  (
  `id` bigint(0) NOT NULL AUTO_INCREMENT,
  `notification_id` bigint(0) NOT NULL COMMENT '消息主表ID',
  `send_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '发送标识',
  `user_id` bigint(0) NOT NULL COMMENT '接收人用户ID',
  `is_read` tinyint(1) NULL DEFAULT 0 COMMENT '0-未读 1-已读',
  `read_time` datetime(0) NULL DEFAULT NULL COMMENT '首次点击阅读的时间',
  `is_deleted` tinyint(1) NOT NULL DEFAULT 0 COMMENT '接收者自己是否删除了这条消息',
  `create_time` datetime(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0),
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_message_user`(`notification_id`, `user_id`) USING BTREE,
  INDEX `idx_receiver_read`(`user_id`, `create_time`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 15 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for sys_operate_log
-- ----------------------------
DROP TABLE IF EXISTS `sys_operate_log`;
CREATE TABLE `sys_operate_log`  (
  `id` bigint(0) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `username` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '操作用户',
  `request_url` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '请求接口',
  `type` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '请求方式',
  `operation_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '操作名称',
  `ip` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT 'ip',
  `source` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT 'ip来源',
  `spend_time` bigint(0) NULL DEFAULT NULL COMMENT '请求接口耗时',
  `create_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP(0) COMMENT '创建时间',
  `params_json` mediumtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT '请求参数',
  `class_path` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '类地址',
  `method_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '方法名',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 876 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for sys_order
-- ----------------------------
DROP TABLE IF EXISTS `sys_order`;
CREATE TABLE `sys_order`  (
  `id` bigint(0) UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '订单ID（主键，自增）',
  `order_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '订单编号（业务唯一，对外展示）',
  `order_type` tinyint(0) UNSIGNED NOT NULL COMMENT '订单类型：1-普通订单 2-秒杀订单 3-拼团订单  4-充值订单',
  `user_id` bigint(0) UNSIGNED NOT NULL COMMENT '用户ID',
  `user_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '用户名称',
  `mobile` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '手机号',
  `address_id` bigint(0) UNSIGNED NULL DEFAULT NULL COMMENT '收货地址ID(shop_consumer_address.id)，下单时填入；NULL=这一单没选收货地址',
  `biz_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '业务ID（关联具体业务，如商品ID、课程ID等）',
  `biz_type` tinyint(0) UNSIGNED NULL DEFAULT NULL COMMENT '业务类型：1-商品 2-课程 3-会员 4-充值 5-提现',
  `total_amount` decimal(12, 2) NOT NULL COMMENT '订单总金额（原价）',
  `pay_amount` decimal(12, 2) NOT NULL COMMENT '实付金额',
  `discount_amount` decimal(12, 2) NOT NULL COMMENT '优惠金额',
  `delivery_fee` decimal(10, 2) NOT NULL COMMENT '配送费（下单时按当时优先级最高的启用规则算出，落库后不再随规则变；已含在 total_amount 里）',
  `fee_rule_id` bigint(0) UNSIGNED NULL DEFAULT NULL COMMENT '算这笔配送费用的是哪条规则(shop_delivery_fee_rule.id)，NULL=没按规则算',
  `currency` char(3) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'CNY' COMMENT '币种',
  `status` tinyint(0) UNSIGNED NOT NULL DEFAULT 0 COMMENT '订单状态：0-待支付 1-已支付 2-已完成 3-已取消 4-已退款 5-已关闭',
  `pay_type` tinyint(0) UNSIGNED NULL DEFAULT NULL COMMENT '支付方式：1-微信 2-支付宝 3-银行卡 4-余额',
  `pay_time` datetime(0) NULL DEFAULT NULL COMMENT '支付时间',
  `expire_time` datetime(0) NULL DEFAULT NULL COMMENT '订单过期时间（超时未支付自动关闭）',
  `remark` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '订单备注',
  `extra` json NULL COMMENT '扩展字段（JSON，存业务个性化数据）',
  `create_time` datetime(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0) COMMENT '创建时间',
  `update_time` datetime(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0) ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '修改时间',
  `is_deleted` tinyint(0) UNSIGNED NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除 1-已删除',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_order_no`(`order_no`) USING BTREE,
  INDEX `idx_user_id`(`user_id`) USING BTREE,
  INDEX `idx_biz_id`(`biz_id`) USING BTREE,
  INDEX `idx_status_create_time`(`status`, `create_time`) USING BTREE,
  INDEX `idx_create_time`(`create_time`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 19 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '订单主表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for sys_refund
-- ----------------------------
DROP TABLE IF EXISTS `sys_refund`;
CREATE TABLE `sys_refund`  (
  `id` bigint(0) UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '退款ID（主键，自增）',
  `refund_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '退款单号（业务唯一，对外展示）',
  `order_id` bigint(0) UNSIGNED NOT NULL COMMENT '订单ID（关联sys_order.id）',
  `order_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '订单编号（冗余，便于查询）',
  `user_id` bigint(0) UNSIGNED NOT NULL COMMENT '用户ID（冗余，便于按用户查询）',
  `user_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '用户名称',
  `mobile` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '手机号',
  `address_id` bigint(0) UNSIGNED NULL DEFAULT NULL COMMENT '收货地址ID(shop_consumer_address.id)，买家提退货时选的那条（寄回/取件地址）；NULL=老数据没选',
  `refund_type` tinyint(0) UNSIGNED NOT NULL DEFAULT 1 COMMENT '退款类型：1-全额退款 2-部分退款',
  `refund_reason` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '退款原因（用户填写）',
  `refund_amount` decimal(12, 2) NOT NULL COMMENT '申请退款金额',
  `actual_refund_amount` decimal(12, 2) NOT NULL COMMENT '实际退款金额（审核后确定）',
  `currency` char(3) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'CNY' COMMENT '币种',
  `status` tinyint(0) UNSIGNED NOT NULL DEFAULT 0 COMMENT '退款状态：0-待审核 2-审核拒绝 3-退款中 4-退款成功 5-退款失败 6-已取消（审核通过直接落 3，没有单独的「已通过」档）',
  `refund_channel` tinyint(0) UNSIGNED NULL DEFAULT NULL COMMENT '退款渠道：1-原路退回 2-余额 3-人工转账（字典口径同实体 SysRefund#refundChannel；本期不真发起退款，没有写入方）',
  `third_refund_no` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '第三方退款单号（微信/支付宝返回）',
  `apply_time` datetime(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0) COMMENT '申请时间',
  `audit_time` datetime(0) NULL DEFAULT NULL COMMENT '审核时间',
  `auditor_id` bigint(0) UNSIGNED NULL DEFAULT NULL COMMENT '审核人ID',
  `audit_remark` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '审核备注',
  `refund_time` datetime(0) NULL DEFAULT NULL COMMENT '退款成功时间',
  `fail_reason` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '退款失败原因',
  `remark` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '备注',
  `extra` json NULL COMMENT '扩展字段（JSON，存退款个性化数据）',
  `create_time` datetime(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0) COMMENT '创建时间',
  `update_time` datetime(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0) ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '修改时间',
  `is_deleted` tinyint(0) UNSIGNED NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除 1-已删除',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_refund_no`(`refund_no`) USING BTREE,
  INDEX `idx_order_id`(`order_id`) USING BTREE,
  INDEX `idx_order_no`(`order_no`) USING BTREE,
  INDEX `idx_user_id`(`user_id`) USING BTREE,
  INDEX `idx_status_create_time`(`status`, `create_time`) USING BTREE,
  INDEX `idx_third_refund_no`(`third_refund_no`) USING BTREE,
  INDEX `idx_create_time`(`create_time`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 4 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '退款表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for sys_role
-- ----------------------------
DROP TABLE IF EXISTS `sys_role`;
CREATE TABLE `sys_role`  (
  `id` int(0) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `code` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL COMMENT '角色编码',
  `name` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL COMMENT '角色名称',
  `remarks` varchar(500) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL COMMENT '角色描述',
  `create_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP(0) COMMENT '创建时间',
  `update_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP(0) COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 20 CHARACTER SET = utf8mb3 COLLATE = utf8mb3_general_ci COMMENT = '角色表 ' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for sys_role_menu
-- ----------------------------
DROP TABLE IF EXISTS `sys_role_menu`;
CREATE TABLE `sys_role_menu`  (
  `id` int(0) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `role_id` int(0) NULL DEFAULT NULL COMMENT '角色ID',
  `menu_id` int(0) NULL DEFAULT NULL COMMENT '菜单ID',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `role_id`(`role_id`, `menu_id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1210 CHARACTER SET = utf8mb3 COLLATE = utf8mb3_general_ci COMMENT = '角色-权限资源关联表 ' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for sys_user
-- ----------------------------
DROP TABLE IF EXISTS `sys_user`;
CREATE TABLE `sys_user`  (
  `id` int(0) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `username` varchar(100) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL COMMENT '账号',
  `password` varchar(100) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL COMMENT '登录密码',
  `openid` varchar(100) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL COMMENT 'openid',
  `sex` int(0) NULL DEFAULT 0 COMMENT '性别 0.未知 1.男 2.女',
  `user_tags` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL COMMENT '标签',
  `create_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP(0) COMMENT '创建时间',
  `update_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP(0) COMMENT '更新时间',
  `status` int(0) NULL DEFAULT 1 COMMENT '状态 0:锁定 1:正常',
  `ip` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL COMMENT 'ip地址',
  `ip_location` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL COMMENT 'ip来源',
  `os` varchar(100) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL COMMENT '登录系统',
  `last_login_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP(0) COMMENT '最后登录时间',
  `browser` varchar(100) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL COMMENT '浏览器',
  `nickname` varchar(100) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL COMMENT '昵称',
  `avatar` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL COMMENT '头像',
  `back_image` varchar(200) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL COMMENT '背景图',
  `qr_img` varchar(200) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL COMMENT '二维码',
  `mobile` varchar(15) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL COMMENT '手机号',
  `email` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL COMMENT '邮箱',
  `login_type` varchar(20) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL COMMENT '登录方式',
  `account_type` tinyint(0) NOT NULL DEFAULT 0 COMMENT '账号类型 0:平台用户 1:门店 2:送货员 3:消费者 4:供应商',
  `signature` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL COMMENT '个性签名',
  `area_code` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL COMMENT '地区编码',
  `area_zh` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL COMMENT '地区中文',
  `longitude` decimal(10, 6) NULL DEFAULT NULL COMMENT '经度(GCJ-02)，前端地图选点写入',
  `latitude` decimal(10, 6) NULL DEFAULT NULL COMMENT '纬度(GCJ-02)，前端地图选点写入',
  `address` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '详细地址（街道、门牌号），地图选点时由高德逆地理自动回填',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `username`(`username`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 29 CHARACTER SET = utf8mb3 COLLATE = utf8mb3_general_ci COMMENT = '用户信息表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for sys_user_role
-- ----------------------------
DROP TABLE IF EXISTS `sys_user_role`;
CREATE TABLE `sys_user_role`  (
  `id` int(0) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `role_id` int(0) NULL DEFAULT NULL COMMENT '角色ID',
  `user_id` int(0) NULL DEFAULT NULL COMMENT '用户ID',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 97 CHARACTER SET = utf8mb3 COLLATE = utf8mb3_general_ci COMMENT = '系统管理 - 用户角色关联表 ' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for sys_web_config
-- ----------------------------
DROP TABLE IF EXISTS `sys_web_config`;
CREATE TABLE `sys_web_config`  (
  `id` bigint(0) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `logo` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT 'logo(文件UID)',
  `name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '网站名称',
  `summary` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '介绍',
  `record_num` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '备案号',
  `web_url` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '网站地址',
  `author` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '作者',
  `author_info` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '个性签名',
  `author_avatar` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '作者头像',
  `ali_pay` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '支付宝收款码',
  `weixin_pay` varchar(150) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '微信收款码',
  `github` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT 'github地址',
  `gitee` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT 'gitee地址',
  `qq_number` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT 'QQ号',
  `qq_group` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT 'QQ群',
  `email` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '邮箱',
  `wechat` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '微信',
  `show_list` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '显示的列表（用于控制邮箱、QQ、QQ群、Github、Gitee、微信是否显示在前端）',
  `login_type_list` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '登录方式列表（用于控制前端登录方式，如账号密码,码云,Github,QQ,微信）',
  `open_comment` tinyint(1) NOT NULL DEFAULT 1 COMMENT '是否开启评论(0:否 1:是)',
  `open_admiration` tinyint(1) NOT NULL DEFAULT 1 COMMENT '是否开启赞赏(0:否， 1:是)',
  `tourist_avatar` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '游客头像',
  `bulletin` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '公告',
  `about_me` mediumtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT '关于我',
  `create_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP(0) COMMENT '创建时间',
  `update_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP(0) COMMENT '更新时间',
  `open_lantern` int(0) NULL DEFAULT NULL COMMENT '开启灯笼',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '网站配置表' ROW_FORMAT = Dynamic;

SET FOREIGN_KEY_CHECKS = 1;
