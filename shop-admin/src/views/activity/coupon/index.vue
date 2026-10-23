<template>
  <div class="app-container">
    <!-- 搜索表单 -->
    <div class="search-wrapper">
      <el-form ref="queryFormRef" :model="queryParams" inline class="search-form">
        <el-form-item label="关键字" prop="keyword">
          <el-input v-model="queryParams.keyword" placeholder="券名称/券码" clearable @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="券类型" prop="couponType">
          <el-select v-model="queryParams.couponType" placeholder="全部" clearable>
            <el-option v-for="item in dict.options(DICT_TYPE)" :key="item.value" :label="item.label"
              :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-select v-model="queryParams.status" placeholder="全部" clearable>
            <el-option v-for="item in dict.options(DICT_STATUS)" :key="item.value" :label="item.label"
              :value="item.value" />
          </el-select>
        </el-form-item>

        <!-- 时间范围 + 按钮同一行（照抄活动、门店培训的 .search-row）：这一层 wrapper 独占第二行 -->
        <div class="search-row">
          <el-form-item label="有效期开始" prop="dateRange">
            <el-date-picker v-model="dateRange" type="daterange" value-format="YYYY-MM-DD" range-separator="至"
              start-placeholder="开始日期" end-placeholder="结束日期" />
          </el-form-item>
          <el-form-item class="search-actions">
            <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
            <el-button icon="Refresh" @click="resetQuery">重置</el-button>
          </el-form-item>
        </div>
      </el-form>
    </div>

    <el-card class="box-card">
      <!-- 操作工具栏 -->
      <template #header>
        <ButtonGroup>
          <el-button v-permission="['activity:coupon:add']" type="primary" plain icon="Plus"
            @click="handleAdd">新增</el-button>
          <el-button v-permission="['activity:coupon:delete']" type="danger" plain icon="Delete"
            :disabled="selectedIds.length === 0" @click="handleBatchDelete">批量删除</el-button>
        </ButtonGroup>
      </template>

      <!-- 数据表格 -->
      <el-table v-loading="loading" :data="dataList" show-overflow-tooltip @selection-change="handleSelectionChange">
        <el-table-column type="selection" width="55" align="center" />
        <el-table-column label="券名称" prop="couponName" align="center" min-width="150" />
        <el-table-column label="状态" align="center" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="dict.tag(DICT_STATUS, row.status)">
              {{ dict.label(DICT_STATUS, row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="券码" prop="couponCode" align="center" min-width="160" />
        <el-table-column label="券类型" align="center" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="dict.tag(DICT_TYPE, row.couponType)">
              {{ dict.label(DICT_TYPE, row.couponType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="优惠" align="center" min-width="140">
          <template #default="{ row }">{{ amountText(row) }}</template>
        </el-table-column>
        
        <el-table-column label="发放/总量" align="center" width="120">
          <template #default="{ row }">{{ row.issuedCount || 0 }} / {{ totalText(row) }}</template>
        </el-table-column>
        <el-table-column label="有效期" align="center" min-width="180">
          <template #default="{ row }">{{ validText(row) }}</template>
        </el-table-column>

        <el-table-column label="操作" align="center" width="220" fixed="right">
          <template #default="{ row }">
            <!-- 四颗按钮由 TableMoreActions 收成"直出两颗 + 更多"（口径同活动、门店培训）。
                 "发出去过的删不掉"是服务层的判据，这里不预告；「发放」不在这排，它在「发放记录」弹窗顶上 -->
            <TableMoreActions :actions="[
              { label: '详情', icon: 'View', hidden: !hasPermission('activity:coupon:detail'), command: { type: 'detail', row } },
              { label: '发放记录', icon: 'Tickets', hidden: !hasPermission('activity:coupon:record'), command: { type: 'record', row } },
              { label: '修改', icon: 'Edit', hidden: !hasPermission('activity:coupon:update'), loading: updatingId === row.id, command: { type: 'update', row } },
              { label: '删除', type: 'danger', icon: 'Delete', hidden: !hasPermission('activity:coupon:delete'), command: { type: 'delete', row } }
            ]" @command="handleActionCommand" />
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页工具栏 -->
      <div class="pagination-container">
        <el-pagination background v-model:current-page="queryParams.pageNum" v-model:page-size="queryParams.pageSize"
          :page-sizes="[10, 20, 30, 50]" :total="total" layout="total, sizes, prev, pager, next, jumper"
          @size-change="handleSizeChange" @current-change="handleCurrentChange" />
      </div>
    </el-card>

    <!-- 添加或修改对话框 -->
    <el-dialog v-model="open" :title="title" width="900px" append-to-body destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="120px">
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="券名称" prop="couponName">
              <el-input v-model="form.couponName" placeholder="请输入券名称" maxlength="200" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="券码" prop="couponCode">
              <!-- 同培训编号：新增不出现可编辑的码，保存后由系统生成（COUPON_+时间戳） -->
              <el-input v-model="form.couponCode" placeholder="保存后由系统生成" disabled />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="券类型" prop="couponType">
              <el-select v-model="form.couponType" placeholder="请选择" style="width: 100%" @change="handleTypeChange">
                <el-option v-for="item in dict.options(DICT_TYPE)" :key="item.value" :label="item.label"
                  :value="item.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="适用门店" prop="storeId">
              <!-- 可空（和门店培训的所属门店是两回事）：不选就是全平台通用，所以给清除按钮 -->
              <el-select v-model="form.storeId" placeholder="不选即全平台" filterable clearable style="width: 100%">
                <el-option v-for="item in storeOptions" :key="item.id" :label="item.storeName" :value="item.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <!-- 这一格跟着券类型换控件：折扣券收的是「折扣百分比」（库里存的还是 0-1 的折扣率，÷100 在 discountPercent 那一头做），
                 满减券/代金券收的是金额，免邮券根本不填这一格（面额恒为 0，留着只会让人以为能填钱） -->
            <el-form-item v-if="!isFreeShippingType" :label="faceValueLabel" prop="faceValue">
              <el-input-number v-if="isDiscountType" v-model="discountPercent" :min="1" :max="100" :precision="0"
                :controls="false" :placeholder="faceValuePlaceholder" :value-on-clear="null" style="width: 100%" />
              <el-input-number v-else v-model="form.faceValue" :min="0" :precision="2" :controls="false"
                :placeholder="faceValuePlaceholder" :value-on-clear="null" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="使用门槛" prop="thresholdAmount">
              <el-input-number v-model="form.thresholdAmount" :min="0" :precision="2" :controls="false"
                placeholder="0 即无门槛" :value-on-clear="null" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="最高抵扣" prop="maxDiscount">
              <!-- 这一格库里可空，空着是"不封顶"，不是漏填，所以给它清除（值落到 null，服务层那格挂的是 IGNORED） -->
              <el-input-number v-model="form.maxDiscount" :min="0" :precision="2" :controls="false"
                placeholder="不封顶就留空" :value-on-clear="null" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="发行总量" prop="totalCount">
              <el-input-number v-model="form.totalCount" :min="0" :precision="0" :controls="false"
                placeholder="0 即不限量" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="适用范围" prop="scopeType">
              <el-select v-model="form.scopeType" placeholder="请选择" style="width: 100%" @change="handleScopeChange">
                <el-option v-for="item in dict.options(DICT_SCOPE)" :key="item.value" :label="item.label"
                  :value="item.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <!-- 范围值这一格按适用范围换成那两颗公共选择器（库里是 json，交出去的是逗号分隔的 scopeIds，服务层拼）：
               两颗都是弹窗里勾一批、确定交回整批，下面那一排 chips 跟着换，单条想摘掉点 × -->
          <el-col v-if="needScopeIds" :span="12">
            <el-form-item :label="`适用${scopeWord}`">
              <shop-product-select v-if="isProductScope" v-model="pickedIds" multiple placeholder="选择商品"
                @select="handleProductPick" />
              <shop-category-select v-else v-model="pickedIds" :data="categoryOptions" multiple placeholder="选择分类"
                @select="handleCategoryPick" />
            </el-form-item>
          </el-col>
          <el-col v-if="needScopeIds" :span="24">
            <el-form-item :label="`已选${scopeWord}`" prop="scopeIds">
              <div v-if="scopeItems.length" class="scope-chips">
                <el-tag v-for="item in scopeItems" :key="item.id" closable type="info"
                  @close="removeScopeItem(item.id)">
                  {{ item.name || `${scopeWord} #${item.id}` }}
                </el-tag>
              </div>
              <span v-else class="chips-empty">还没选，弹窗里可以一次勾多个</span>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="有效期类型" prop="validType">
              <el-select v-model="form.validType" placeholder="请选择" style="width: 100%" @change="handleValidChange">
                <el-option v-for="item in dict.options(DICT_VALID)" :key="item.value" :label="item.label"
                  :value="item.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <!-- 有效期那一格按类型换控件：2 领取后N天 只填天数，1 固定时间段 填两头 -->
          <el-col v-if="form.validType === VALID_AFTER_DAYS" :span="12">
            <el-form-item label="有效天数" prop="validDays">
              <el-input-number v-model="form.validDays" :min="1" :precision="0" :controls="false"
                placeholder="领取之日起几天内有效" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col v-else :span="12">
            <el-form-item label="有效期开始" prop="validStart">
              <el-date-picker v-model="form.validStart" type="datetime" value-format="YYYY-MM-DD HH:mm:ss"
                placeholder="不填即不限" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col v-if="form.validType !== VALID_AFTER_DAYS" :span="12">
            <el-form-item label="有效期结束" prop="validEnd">
              <!-- 发放时用户券的过期时间就取这一格，所以这一档不填等于这张券发不出去（服务层拦） -->
              <el-date-picker v-model="form.validEnd" type="datetime" value-format="YYYY-MM-DD HH:mm:ss"
                placeholder="发放前必填，不填发不出去" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="领取方式" prop="getWay">
              <el-select v-model="form.getWay" placeholder="请选择" style="width: 100%">
                <el-option v-for="item in dict.options(DICT_GET_WAY)" :key="item.value" :label="item.label"
                  :value="item.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="负责的运营" prop="userId">
              <!-- 只登记到人，列表不按它收窄（口径同门店培训的培训讲师）。候选要 0 平台 + 1 门店两类：
                   门店自己发的券就该由那个门店账号负责，送货员/消费者/供应商/ERP 不该出现在这儿 -->
              <user-select v-model="form.userId" :label="form.userName" :account-type="[0, 1]" only-enabled
                placeholder="选择系统用户" @select="handleUserSelect" @clear="handleUserClear" />
            </el-form-item>
          </el-col>
          <!-- 状态只有修改时才给选：新增一律落成 1 未开始（服务层 insert 里钉死），
               给一个选了的值也会被覆盖，不如不出现 -->
          <el-col v-if="form.id !== undefined" :span="12">
            <el-form-item label="状态" prop="status">
              <el-select v-model="form.status" placeholder="请选择" style="width: 100%">
                <el-option v-for="item in dict.options(DICT_STATUS)" :key="item.value" :label="item.label"
                  :value="item.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="已发放" prop="issuedCount">
              <!-- 这一格改不动（实体钉了 NEVER，只有「发放」会加它）：给个禁用框看数，比在备注里解释清楚 -->
              <el-input :model-value="form.issuedCount ?? 0" disabled />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="使用说明" prop="description">
              <el-input v-model="form.description" type="textarea" :rows="2"
                placeholder="给用户看的使用范围、叠加约定等" maxlength="500" show-word-limit />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="cancel">取 消</el-button>
          <el-button type="primary" :loading="submitLoading" @click="submitForm">确 定</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 详情：只读看这张券的全部登记信息（口径同活动、门店培训那两页的详情弹窗），要改内容走「修改」 -->
    <el-dialog v-model="detailOpen" title="优惠券详情" width="900px" append-to-body destroy-on-close>
      <el-descriptions :column="2" border v-loading="detailLoading">
        <el-descriptions-item label="券名称" width="150px">{{ detail.couponName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="券码">{{ detail.couponCode || '-' }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag size="small" :type="dict.tag(DICT_STATUS, detail.status)">
            {{ dict.label(DICT_STATUS, detail.status) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="券类型">
          <el-tag size="small" :type="dict.tag(DICT_TYPE, detail.couponType)">
            {{ dict.label(DICT_TYPE, detail.couponType) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="优惠">{{ amountText(detail) }}</el-descriptions-item>
        <el-descriptions-item label="使用门槛">{{ thresholdText(detail) }}</el-descriptions-item>
        <el-descriptions-item label="最高抵扣">{{ detail.maxDiscount ? `${detail.maxDiscount} 元` : '不封顶' }}</el-descriptions-item>
        <el-descriptions-item label="适用范围">{{ scopeText(detail) }}</el-descriptions-item>
        <el-descriptions-item label="适用门店">
          {{ detail.storeName || (detail.storeId ? `已删除门店 #${detail.storeId}` : '全平台') }}
        </el-descriptions-item>
        <el-descriptions-item label="负责的运营">{{ detail.userName || '未指定' }}</el-descriptions-item>
        <el-descriptions-item label="发行总量">{{ totalText(detail) }}</el-descriptions-item>
        <el-descriptions-item label="已发放">{{ detail.issuedCount || 0 }}</el-descriptions-item>
        <el-descriptions-item label="可发放">{{ restText(detail) }}</el-descriptions-item>
        <el-descriptions-item label="已使用">{{ detail.usedCount || 0 }}</el-descriptions-item>
        <el-descriptions-item label="有效期" :span="2">{{ validText(detail) }}</el-descriptions-item>
        <el-descriptions-item label="领取方式">
          {{ dict.label(DICT_GET_WAY, detail.getWay) }}
        </el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ detail.createTime || '-' }}</el-descriptions-item>
        <el-descriptions-item label="使用说明" :span="2">{{ detail.description || '-' }}</el-descriptions-item>
        <el-descriptions-item label="修改时间" :span="2">{{ detail.updateTime || '-' }}</el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="detailOpen = false">关 闭</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 发放记录：这张券发到哪些用户手上了（一批发放一行，只读，没有修改/删除）。
         顶上那行是「发行总量 / 已发放 / 可发放量」—— 都是列表那一行带回的额度数，和明细同一张表，所以对得上；
         「发放」那颗按钮在右边，它占的就是这行说的可发放量 -->
    <el-dialog v-model="recordOpen" :title="`发放记录 - ${recordCoupon.couponName || ''}`" width="1000px" append-to-body
      destroy-on-close>
      <div class="record-toolbar">
        <span class="record-summary">
          发行总量 {{ totalText(recordCoupon) }} / 已发放 {{ recordCoupon.issuedCount || 0 }} / 可发放
          {{ restText(recordCoupon) }}
        </span>
        <div class="record-controls">
          <el-select v-model="recordQuery.status" placeholder="全部状态" clearable class="record-filter"
            @change="handleRecordFilter">
            <el-option v-for="item in dict.options(DICT_USER_STATUS)" :key="item.value" :label="item.label"
              :value="item.value" />
          </el-select>
          <el-button v-permission="['activity:coupon:give']" type="primary" plain icon="Promotion" class="record-give"
            @click="handleGive">发放</el-button>
        </div>
      </div>

      <el-table :data="recordList" size="small" border v-loading="recordLoading" show-overflow-tooltip
        empty-text="这张券还没有发放记录">
        <el-table-column label="券码" prop="couponCode" align="center" min-width="150" />
        <el-table-column label="持券用户" align="center" min-width="120">
          <template #default="{ row }">{{ recordUserText(row) }}</template>
        </el-table-column>
        <el-table-column label="手机号" align="center" width="130">
          <template #default="{ row }">{{ row.mobile || '-' }}</template>
        </el-table-column>
        <el-table-column label="状态" align="center" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="dict.tag(DICT_USER_STATUS, row.status)">
              {{ dict.label(DICT_USER_STATUS, row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="领取方式" align="center" width="100">
          <template #default="{ row }">{{ dict.label(DICT_GET_WAY, row.getWay) }}</template>
        </el-table-column>
        <el-table-column label="领取时间" align="center" width="160">
          <template #default="{ row }">{{ validate.formatTime(row.receiveTime) || '-' }}</template>
        </el-table-column>
        <el-table-column label="过期时间" align="center" width="160">
          <template #default="{ row }">{{ validate.formatTime(row.expireTime) || '-' }}</template>
        </el-table-column>
        <el-table-column label="使用时间" align="center" width="160">
          <template #default="{ row }">{{ validate.formatTime(row.useTime) || '-' }}</template>
        </el-table-column>
        <el-table-column label="备注" prop="remark" align="center" min-width="140">
          <template #default="{ row }">{{ row.remark || '-' }}</template>
        </el-table-column>
      </el-table>

      <div class="pagination-container">
        <el-pagination background size="small" v-model:current-page="recordQuery.pageNum"
          v-model:page-size="recordQuery.pageSize" :page-sizes="[10, 20, 50]" :total="recordTotal"
          layout="total, sizes, prev, pager, next, jumper" @size-change="handleRecordSizeChange"
          @current-change="getRecordList" />
      </div>

      <template #footer>
        <div class="dialog-footer">
          <el-button @click="recordOpen = false">关 闭</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 发放：一次给一批用户发券，每人几张是「发放数量」那一格说的。参数只送「哪张券、发给谁、每人几张、备注」四格，
         面额那几格由后端按模板当场快照（前端传面额进来就能发出一张不存在的券） -->
    <el-dialog v-model="giveOpen" :title="`发放优惠券 - ${recordCoupon.couponName || ''}`" width="720px" append-to-body
      destroy-on-close>
      <el-form label-width="110px">
        <el-form-item label="选择用户">
          <!-- 多选：弹窗里一次勾一批、确定交回整批，下面那排 chips 跟着换（单条想摘掉点 ×） -->
          <user-select v-model="pickedUserIds" :account-type="3" multiple placeholder="选择要发放的消费者"
            @select="handleGiveSelect" />
        </el-form-item>
        <el-form-item label="已选用户">
          <div v-if="giveForm.users.length" class="user-chips">
            <el-tag v-for="item in giveForm.users" :key="item.id" closable type="info" @close="removeGiveUser(item.id)">
              {{ item.nickname || `用户 #${item.id}` }}
            </el-tag>
          </div>
          <span v-else class="chips-empty">还没选人，弹窗里可以一次勾多个</span>
        </el-form-item>
        <el-form-item label="发放数量">
          <el-input-number v-model="giveForm.quantity" :min="1" controls-position="right" />
          <span class="give-tip give-quantity-tip">选中的每个用户各发 {{ giveForm.quantity }} 张</span>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="giveForm.remark" type="textarea" :rows="2" placeholder="例如：618 补偿发放" maxlength="255"
            show-word-limit />
        </el-form-item>
        <el-form-item label=" ">
          <!-- 这一行说的是这次点确定会发生什么，包括两个后端也拦的数：本次要占的张数（人数 × 每人张数）和这张券剩下的量。
               不限量时后端只在"选 0 个人"时才报，所以这里也只跟着报这一条 -->
          <span class="give-tip">
            本次发放 {{ giveCount }} 张（{{ giveForm.users.length }} 人 × 每人 {{ giveForm.quantity }} 张），这张券当前可发放
            {{ restText(recordCoupon) }}<template v-if="giveForm.users.length > 0">（已发放会变成
              {{ (recordCoupon.issuedCount || 0) + giveCount }}）</template>
          </span>
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="giveOpen = false">取 消</el-button>
          <el-button type="primary" :loading="giveLoading" :disabled="giveForm.users.length === 0"
            @click="submitGive">确 定</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import {
  listShopCouponApi,
  detailShopCouponApi,
  addShopCouponApi,
  updateShopCouponApi,
  deleteShopCouponApi,
  listShopCouponRecordApi,
  giveShopCouponApi
} from '@/api/activity/coupon'
import { optionsShopStoreApi } from '@/api/store/store'
import { getCategoryTreeApi } from '@/api/shop/category'
import { listShopProductApi } from '@/api/shop/product'
import ButtonGroup from '@/components/ButtonGroup/index.vue'
import UserSelect from '@/components/UserSelect/index.vue'
import TableMoreActions from '@/components/TableMoreActions/index.vue'
import ShopProductSelect from '@/components/ShopProductSelect/index.vue'
import ShopCategorySelect from '@/components/ShopCategorySelect/index.vue'
import validate from '@/utils/validate'
import { useDict } from '@/utils/dictCache'
import { useUserStore } from '@/store/modules/user'

const userStore = useUserStore()
/** 操作列走 TableMoreActions，权限得在这里自己判（组件没有 v-permission 那套指令，只认 actions 上的 hidden） */
const permissions = computed(() => userStore.user.permissions || [])
const hasPermission = (permission: string): boolean => {
  return permissions.value.includes(permission)
}

const DICT_TYPE = 'coupon_type'
const DICT_VALID = 'valid_type'
const DICT_SCOPE = 'scope_type'
const DICT_GET_WAY = 'get_way'
const DICT_STATUS = 'coupon_status'
/** 「发放记录」弹窗里那一格用户券状态（0未使用 1已使用 2已过期 3已作废），筛选项和列表列都用它 */
const DICT_USER_STATUS = 'coupon_user_status'

const dict = useDict(DICT_TYPE, DICT_VALID, DICT_SCOPE, DICT_GET_WAY, DICT_STATUS, DICT_USER_STATUS)

/** 判据要和服务层对齐的几档：折扣券那一格收折扣百分比（库里存的是折扣率，换算见 discountPercent）、免邮券不用填面额、只有指定商品/分类需要范围值 */
const TYPE_DISCOUNT = 2
const TYPE_FREE_SHIPPING = 3
const VALID_AFTER_DAYS = 2
const SCOPE_PRODUCT = 2
const SCOPE_CATEGORY = 3

const loading = ref(true)
const submitLoading = ref(false)
const selectedIds = ref<any[]>([])
const total = ref(0)
const dataList = ref<any[]>([])
const title = ref('')
const open = ref(false)
const detailOpen = ref(false)
const detailLoading = ref(false)
const detail = ref<any>({})
const dateRange = ref<string[]>([])

/* ---------------- 发放记录（用户券那一头）：列表页内的只读弹窗，写法照活动页的「查看记录」 ---------------- */
const recordOpen = ref(false)
const recordLoading = ref(false)
const recordList = ref<any[]>([])
const recordTotal = ref(0)
/** 弹窗标题和顶上那行额度用的是列表那一行带回的数据；发完之后这一格会按新数据换一次 */
const recordCoupon = ref<any>({})
const recordQuery = reactive<any>({
  pageNum: 1,
  pageSize: 10,
  couponId: undefined,
  status: undefined
})

/* ---------------- 发放（这一页唯一的一条写入路，占的就是上面那行说的可发放量） ---------------- */
const giveOpen = ref(false)
const giveLoading = ref(false)
const giveForm = reactive<any>({
  users: [] as any[],
  // 每人发几张：上限不在这里挡，够不够由后端那条带总量校验的原子 UPDATE 判（报「可发放量不够」）
  quantity: 1,
  remark: undefined
})

/**
 * 那颗用户选择器的多选值：勾选清单以 giveForm.users 为准，这里只做双向映射，
 * 所以 chips 上点 × 摘掉一个人，下次开弹窗就不会再被勾上
 */
const pickedUserIds = computed<Array<number | string>>({
  get: () => giveForm.users.map((item: any) => item.id),
  set: (ids) => {
    giveForm.users = giveForm.users.filter((item: any) => ids.some((id) => String(id) === String(item.id)))
  }
})

const queryFormRef = ref<FormInstance>()
const formRef = ref<FormInstance>()

// 查询参数
const queryParams = reactive<any>({
  pageNum: 1,
  pageSize: 10,
  keyword: undefined,
  couponType: undefined,
  status: undefined
})

// 表单参数
const form = reactive<any>({})

/** 范围值只在指定商品/指定分类这两档出现（1 全场、4 指定门店都不填它，判据同服务层 applyScopeIds） */
const needScopeIds = computed(() => {
  const type = Number(form.scopeType)
  return type === SCOPE_PRODUCT || type === SCOPE_CATEGORY
})

/** 下拉里 dict.options 给的 value 是字符串，比档位一律先 Number()，不然 '2' === 2 永远不成立 */
const isProductScope = computed(() => Number(form.scopeType) === SCOPE_PRODUCT)

/** 那一格叫什么、chips 上的兜底怎么写，都跟着适用范围换 */
const scopeWord = computed(() => (isProductScope.value ? '商品' : '分类'))

/** 面额那一格跟着券类型换档：2 折扣券 收的是折扣百分比，3 免邮券 压根不填这一格 */
const isDiscountType = computed(() => Number(form.couponType) === TYPE_DISCOUNT)
const isFreeShippingType = computed(() => Number(form.couponType) === TYPE_FREE_SHIPPING)

const faceValueLabel = computed(() => (isDiscountType.value ? '折扣（%）' : '面额（元）'))

const faceValuePlaceholder = computed(() => (isDiscountType.value ? '9 折填 90，最高 99' : '减免金额，如 20'))

/**
 * 折扣券那一格交出去的是百分比、库里 `face_value` 存的是折扣率（DDL 写的「折扣率如0.9」、服务层也按 0<x<1 拦），
 * 换算只在这一头做，表单值始终是库里的口径，提交和回显都不用再管单位。
 * 取整数：`face_value` 是 decimal(10,2)，能表示的最小折扣粒度正好是 1%，百分比带小数存进去就被舍了。
 */
const discountPercent = computed<number | null>({
  get: () => {
    const rate = Number(form.faceValue)
    if (form.faceValue === undefined || form.faceValue === null || form.faceValue === '' || isNaN(rate)) return null
    return Math.round(rate * 100)
  },
  set: (value) => {
    form.faceValue = value === null || value === undefined ? null : Number((value / 100).toFixed(2))
  }
})

/**
 * 换券类型就把这一格清掉：它的意思跟着类型换（元 / 折扣百分比），
 * 留着上一个类型填的 20 就是拿 20 块钱当 20%。免邮券那档置 0，因为这列 NOT NULL、留着旧值会一路带到详情里
 */
const handleTypeChange = () => {
  form.faceValue = isFreeShippingType.value ? 0 : undefined
  formRef.value?.clearValidate(['faceValue'])
}

/**
 * 前端这一道只钉"服务层一定会报"的几格，别比后端还严：
 * 有效期结束这些都是"新建先占个名额、细则后定"的常态，靠发放那一刀去拦（resolveExpireTime）
 */
const rules = computed<FormRules>(() => ({
  couponName: [{ required: true, message: '券名称不能为空', trigger: 'blur' }],
  couponType: [{ required: true, message: '请选择券类型', trigger: 'change' }],
  validType: [{ required: true, message: '请选择有效期类型', trigger: 'change' }],
  scopeType: [{ required: true, message: '请选择适用范围', trigger: 'change' }],
  getWay: [{ required: true, message: '请选择领取方式', trigger: 'change' }],
  totalCount: [{ required: true, message: '发行总量不能为空，不限量就填 0', trigger: 'change' }],
  validDays: form.validType === VALID_AFTER_DAYS
    ? [{ required: true, message: '「领取后N天」这一档得填有效天数', trigger: 'change' }]
    : [],
  // 免邮券那一格不渲染，规则也不能留，否则 validate 会去查一个不在场的字段
  faceValue: isFreeShippingType.value ? [] : isDiscountType.value
    ? [{
      required: true,
      validator: (_rule: any, _value: any, callback: any) => {
        if (discountPercent.value === null) callback(new Error('折扣券得填折扣，9 折填 90'))
        else if (discountPercent.value >= 100) callback(new Error('折扣填 100 就是不打折，最高只能到 99'))
        else callback()
      },
      trigger: ['blur', 'change']
    }]
    : [{
      required: true,
      validator: (_rule: any, _value: any, callback: any) => {
        const face = Number(form.faceValue)
        if (form.faceValue === undefined || form.faceValue === null || form.faceValue === '') {
          callback(new Error('这张券要减多少钱，总得填'))
        } else if (face <= 0) {
          callback(new Error('填 0 就等于不发任何东西（免邮券才不用填这一格）'))
        } else if (Number(form.thresholdAmount) > 0 && face > Number(form.thresholdAmount)) {
          // 「满」不低于「减」：服务层同款判据再来一次，满 20 减 50 那种是白送钱
          callback(new Error(`减的比门槛还多，这张券是满 ${form.thresholdAmount} 减 ${face}`))
        } else {
          callback()
        }
      },
      trigger: ['blur', 'change']
    }],
  scopeIds: needScopeIds.value
    ? [{ required: true, message: `还要从下面挑${scopeWord.value}`, trigger: ['blur', 'change'] }]
    : [],
  // 固定时间段那两头的先后：服务层同款判据再来一次，谁先命中都是同一句话
  validEnd: [{
    validator: (_rule: any, _value: any, callback: any) => {
      if (form.validStart && form.validEnd && form.validEnd < form.validStart) {
        callback(new Error('有效期结束时间不能早于开始时间'))
      } else {
        callback()
      }
    },
    trigger: 'change'
  }]
}))

/** 门店候选：表单那一格「适用门店」用（options 返回启用的门店，口径同活动页） */
const storeOptions = ref<any[]>([])

const getStoreOptions = async () => {
  try {
    const { data } = await optionsShopStoreApi()
    storeOptions.value = data || []
  } catch (error) {
  }
}

/** 选中运营：只回填昵称给选择器回显（库里只有 user_id，名字联表现取） */
const handleUserSelect = (user: any) => {
  form.userName = user.nickname || user.username
}

const handleUserClear = () => {
  form.userName = undefined
}

/**
 * 范围值那一排已选的东西。两颗公共选择器这一格是多选的（弹窗里勾一批、确定一次性交回整批），
 * 所以确定等于整批替换；form.scopeIds 由这一排重新拼成逗号串（服务层再按 scope_type 拼成 json）。
 */
const scopeItems = ref<any[]>([])

/** 选择器里的多选值只用来给弹窗回显勾了哪几行，真正的已选清单归 scopeItems */
const pickedIds = computed<Array<number | string>>({
  get: () => scopeItems.value.map((item: any) => item.id),
  set: (ids) => {
    // 清空选择器就是把这一批全清。确定时组件先抛 ids 再抛整行，所以这一步只负责减掉没勾的那些
    scopeItems.value = scopeItems.value.filter((item: any) => ids.some((id) => String(id) === String(item.id)))
    syncScopeIds()
  }
})

const syncScopeIds = () => {
  form.scopeIds = scopeItems.value.length ? scopeItems.value.map((item: any) => item.id).join(',') : undefined
  // 这一格的值是这里自己拼进 form 的，不是那两颗选择器直接改的，EP 的 change 校验轮不到，手动清一次报错
  if (scopeItems.value.length > 0) {
    formRef.value?.clearValidate(['scopeIds'])
  }
}

const removeScopeItem = (id: any) => {
  scopeItems.value = scopeItems.value.filter((item: any) => String(item.id) !== String(id))
  syncScopeIds()
}

/** 整批替换：弹窗里勾掉哪个，chips 上就少哪个，不在这里做增量累加 */
const handleProductPick = (rows: any[]) => {
  scopeItems.value = (rows || []).map((row: any) => ({ id: row.id, name: row.productName }))
  syncScopeIds()
}

const handleCategoryPick = (rows: any[]) => {
  scopeItems.value = (rows || []).map((row: any) => ({ id: row.id, name: row.categoryName }))
  syncScopeIds()
}

/** 换了适用范围就把上一档攒下的那批清掉重选：商品 id 留在分类那一格里，落库就是给下一个用的人挖坑 */
const handleScopeChange = () => {
  scopeItems.value = []
  form.scopeIds = undefined
}

/** 分类候选（树）：那一格换成分类树，口径同商品页的所属分类、活动页的跳转值 */
const categoryOptions = ref<any[]>([])

const getCategoryOptions = async () => {
  try {
    const { data } = await getCategoryTreeApi({})
    categoryOptions.value = data || []
  } catch (error) {
  }
}

const findCategoryNode = (nodes: any[], id: any): any => {
  for (const node of nodes || []) {
    if (String(node.id) === String(id)) return node
    const matched = findCategoryNode(node.children, id)
    if (matched) return matched
  }
  return null
}

const categoryNodeName = (id: any) => {
  const node = findCategoryNode(categoryOptions.value, id)
  return node ? node.categoryName : ''
}

/**
 * 修改弹窗打开时把库里那串 id 补成带名字的 chips：
 * 分类在已经捞回来的树里现查（零请求），商品走列表接口那一条按 ids 批量取（一次请求，不逐个捞）。
 * 取不到名字的（商品删了，或者已经不在当前账号的数据范围里）留空，chips 上回落成「商品 #id」，
 * 同「适用门店」那一格的兜底写法。
 */
const loadScopeItems = async () => {
  scopeItems.value = []
  const ids = String(form.scopeIds || '').split(',').filter((text: string) => text !== '')
  if (!ids.length) return
  if (!isProductScope.value) {
    if (!categoryOptions.value.length) await getCategoryOptions()
    scopeItems.value = ids.map((id: string) => ({ id, name: categoryNodeName(id) }))
    return
  }
  let rows: any[] = []
  try {
    const { data } = await listShopProductApi({ pageNum: 1, pageSize: ids.length, ids: ids.join(',') })
    rows = data.records || []
  } catch (error) {
  }
  scopeItems.value = ids.map((id: string) => ({
    id,
    name: rows.find((row: any) => String(row.id) === id)?.productName || ''
  }))
}

/** 换了有效期类型同理：天数和时间段是两套数，留着旧的会被当成"已经填过" */
const handleValidChange = () => {
  form.validStart = undefined
  form.validEnd = undefined
  form.validDays = undefined
}

/** 一位小数以下的数别拖一串 0：20.50 写成 20.5 */
const numText = (value: any) => {
  const n = Number(value)
  if (!Number.isFinite(n)) return ''
  return String(Number(n.toFixed(2)))
}

/** 最高抵扣是四档券共用的一格（这张券整单最多抵这么多钱，空着就是不封顶），所以四档都要说出来 */
const withCap = (row: any, text: string) => {
  const cap = Number(row.maxDiscount)
  return cap > 0 ? `${text}（最高抵 ${numText(cap)} 元）` : text
}

/** 金额那几格显示成一句人话：满 100 减 20 / 9折 / 免配送费，填了封顶就在后面补一句 */
const amountText = (row: any) => {
  const type = Number(row.couponType)
  if (type === TYPE_FREE_SHIPPING) return withCap(row, '免配送费')
  const face = numText(row.faceValue)
  if (!face || Number(face) <= 0) return '-'
  if (type === TYPE_DISCOUNT) return withCap(row, `${numText(Number(row.faceValue) * 10)}折`)
  const threshold = Number(row.thresholdAmount) || 0
  return withCap(row, threshold > 0 ? `满 ${numText(threshold)} 减 ${face}` : `无门槛减 ${face}`)
}

const thresholdText = (row: any) => {
  const threshold = Number(row.thresholdAmount) || 0
  return threshold > 0 ? `满 ${numText(threshold)} 可用` : '无门槛'
}

/** 适用范围那一格：范围名 + 那串 id（库里是 json，后端已经拆成逗号分隔的 scopeIds 给前端） */
const scopeText = (row: any) => {
  const label = dict.label(DICT_SCOPE, row.scopeType)
  if (!row.scopeIds) return label || '-'
  return `${label}：${row.scopeIds}`
}

/** 发行总量那一格：0 是不限量，别显示成一个 0 看着像发完了 */
const totalText = (row: any) => (Number(row.totalCount) > 0 ? String(row.totalCount) : '不限')

/** 可发放量 = 总量 − 已发放；不限量给"不限"，负数（总量被改小过）按 0 说，和后端那条报错同一口径 */
const restText = (row: any) => {
  const quota = Number(row.totalCount) || 0
  if (quota <= 0) return '不限'
  return String(Math.max(quota - (Number(row.issuedCount) || 0), 0))
}

/** 有效期那一格：2 领取后N天 说天数，1 固定时间段 说两头（口径同活动页的投放时间） */
const validText = (row: any) => {
  if (Number(row.validType) === VALID_AFTER_DAYS) {
    return row.validDays ? `领取后 ${row.validDays} 天内有效` : '还没定有效天数'
  }
  const s = validate.formatTime(row.validStart, 'YYYY-MM-DD HH:mm')
  const e = validate.formatTime(row.validEnd, 'MM-DD HH:mm')
  if (!s && !e) return '不限'
  return [s, e].filter(Boolean).join(' ~ ')
}

/** 查询列表 */
const getList = async () => {
  loading.value = true
  try {
    const params: any = { ...queryParams }
    if (dateRange.value && dateRange.value.length === 2) {
      // 筛的是 valid_start（有效期开始）；这两个查询字段不叫 beginTime/endTime，
      // 是因为后端实体上 valid_start/valid_end 已经是本表的两列，撞了名就没法同时当查询条件用
      params.startFrom = `${dateRange.value[0]} 00:00:00`
      params.startTo = `${dateRange.value[1]} 23:59:59`
    }
    const { data } = await listShopCouponApi(params)
    dataList.value = data.records || []
    total.value = data.total || 0
  } catch (error) {
  } finally {
    loading.value = false
  }
}

/** 表单重置 */
const reset = () => {
  Object.assign(form, {
    id: undefined,
    couponCode: undefined,
    couponName: undefined,
    couponType: dict.defaultValue(DICT_TYPE, 1),
    storeId: undefined,
    storeName: undefined,
    userId: undefined,
    userName: undefined,
    faceValue: undefined,
    thresholdAmount: undefined,
    maxDiscount: undefined,
    totalCount: 0,
    issuedCount: undefined,
    usedCount: undefined,
    validType: dict.defaultValue(DICT_VALID, 1),
    validStart: undefined,
    validEnd: undefined,
    validDays: undefined,
    scopeType: dict.defaultValue(DICT_SCOPE, 1),
    scopeIds: undefined,
    getWay: dict.defaultValue(DICT_GET_WAY, 1),
    status: 1,
    description: undefined
  })
  formRef.value?.resetFields()
  // 已选范围那一排是本页自己攒的（库里只有 id 串），跟表单一起清；选择器里的勾选跟着这一排走
  scopeItems.value = []
}

/** 取消按钮 */
const cancel = () => {
  open.value = false
  reset()
}

/** 搜索按钮操作 */
const handleQuery = () => {
  queryParams.pageNum = 1
  getList()
}

/** 重置按钮操作 */
const resetQuery = () => {
  queryFormRef.value?.resetFields()
  queryParams.keyword = undefined
  queryParams.couponType = undefined
  queryParams.status = undefined
  dateRange.value = []
  handleQuery()
}

/** 多选框选中数据 */
const handleSelectionChange = (selection: { id: any }[]) => {
  selectedIds.value = selection.map((item) => item.id)
}

/**
 * 两格候选都在打开弹窗前捞：门店那一格直接下拉，分类那一格是树（商品的选择器自己懒加载）。
 * 返回 Promise 是给修改弹窗用的 —— 已选范围那一排要靠这棵树把分类 id 补成名字，得等它回来
 */
const loadFormOptions = () => Promise.all([getStoreOptions(), getCategoryOptions()])

/** 新增按钮操作 */
const handleAdd = () => {
  reset()
  loadFormOptions()
  open.value = true
  title.value = '添加优惠券'
}

/** 正在打开修改弹窗的那一张券 id：详情 + 两格候选 + 已选范围的名字都要等，按钮转圈期间顺手挡住连点 */
const updatingId = ref<number | string>()

/** 修改按钮操作 */
const handleUpdate = async (row: any) => {
  updatingId.value = row.id
  reset()
  try {
    const { data } = await detailShopCouponApi(row.id)
    // 先等树回来再补已选范围的名字，否则分类只能显示成「分类 #id」
    await loadFormOptions()
    Object.assign(form, data)
    open.value = true
    title.value = '修改优惠券'
    // 已选范围那一排要按 id 补回名字（库里那串只有 id），所以表单值一就位就补一次
    await loadScopeItems()
  } catch (error) {
  } finally {
    updatingId.value = undefined
  }
}

/** 详情按钮操作 */
const handleDetail = async (row: any) => {
  detail.value = {}
  detailOpen.value = true
  detailLoading.value = true
  try {
    const { data } = await detailShopCouponApi(row.id)
    detail.value = data || {}
  } catch (error) {
  } finally {
    detailLoading.value = false
  }
}

/**
 * 「发放记录」：这张券发到哪些用户手上了（一行一张，只读）。
 * 明细只有后台「发放」那一条路会写（POST /activity/coupon/give），这一页不开新增/删除 ——
 * 手工插一行就能把顶上那三个额度数编出来。
 * 汇总那三格直接用列表行已经带回的（issued_count/total_count 都是库里存的），不再发一次请求。
 */
const handleRecord = (row: any) => {
  recordCoupon.value = row
  // 这一份明细只说列表点进来的这一张券：不带 couponId 后端就不按券筛，会把所有券的发放记录混在一起
  recordQuery.couponId = row.id
  recordQuery.pageNum = 1
  // 换一张券看，状态筛选项要跟着清掉，否则上一张筛的「只有已使用」会跟过来
  recordQuery.status = undefined
  recordList.value = []
  recordTotal.value = 0
  recordOpen.value = true
  getRecordList()
}

const getRecordList = async () => {
  recordLoading.value = true
  try {
    const { data } = await listShopCouponRecordApi(recordQuery)
    recordList.value = data.records || []
    recordTotal.value = data.total || 0
  } catch (error) {
  } finally {
    recordLoading.value = false
  }
}

/** 弹窗里换了状态：回到第一页重数，不然停在第 5 页筛完只剩两行会看着像没数据 */
const handleRecordFilter = () => {
  recordQuery.pageNum = 1
  getRecordList()
}

const handleRecordSizeChange = () => {
  recordQuery.pageNum = 1
  getRecordList()
}

/** 记录里那一格用户名：账号被删了就联不上昵称，按 id 说清是谁，别说成"没查出来" */
const recordUserText = (row: any) => {
  if (row.userName) return row.userName
  return row.userId ? `用户 #${row.userId}` : '-'
}

/**
 * 「发放」：弹窗顶上那颗按钮。用户选择器这一格是多选的（一次在弹窗里勾一批、确定交回整批），
 * 交回来的整批直接替掉 users，去重由勾选本身保证（同一个人不可能勾出两条）
 */
const handleGive = () => {
  giveForm.users = []
  giveForm.quantity = 1
  giveForm.remark = undefined
  giveOpen.value = true
}

/** 这一次点确定要占掉的张数（人数 × 每人张数），提示那一行和「已发放会变成」都用它 */
const giveCount = computed(() => giveForm.users.length * giveForm.quantity)

const handleGiveSelect = (rows: any[]) => {
  giveForm.users = (rows || []).map((user: any) => ({ id: user.id, nickname: user.nickname || user.username }))
}

const removeGiveUser = (id: any) => {
  giveForm.users = giveForm.users.filter((item: any) => String(item.id) !== String(id))
}

/** 提交发放：一颗「确定」在没选人时是禁用状态，所以这一路不需要再走一遍表单校验 */
const submitGive = async () => {
  if (giveForm.users.length === 0) return
  giveLoading.value = true
  try {
    const { data } = await giveShopCouponApi({
      couponId: recordCoupon.value.id,
      userIds: giveForm.users.map((item: any) => item.id),
      quantity: giveForm.quantity,
      remark: giveForm.remark
    })
    ElMessage.success(`已发放 ${data} 张`)
    giveOpen.value = false
    // 额度那三格是列表行带回的，所以要先重查列表、再按新的那一行刷新弹窗和明细
    await getList()
    refreshRecordCoupon()
    getRecordList()
  } catch (error) {
  } finally {
    giveLoading.value = false
  }
}

/** 发完之后把弹窗顶上那行汇总换成列表里刚查回来的那一行（还是那三个数，只是新值） */
const refreshRecordCoupon = () => {
  const fresh = dataList.value.find((item: any) => item.id === recordCoupon.value.id)
  if (fresh) {
    recordCoupon.value = fresh
  }
}

/** 提交按钮 */
const submitForm = () => {
  formRef.value?.validate(async (valid: any) => {
    if (!valid) return
    submitLoading.value = true
    try {
      if (form.id !== undefined) {
        await updateShopCouponApi(form)
        ElMessage.success('修改成功')
      } else {
        await addShopCouponApi(form)
        ElMessage.success('新增成功')
      }
      open.value = false
      getList()
    } catch (error) {
    } finally {
      submitLoading.value = false
    }
  })
}

/** 删除按钮操作 */
const handleDelete = (row: any) => {
  ElMessageBox.confirm(`确认删除「${row.couponName}」这张优惠券吗？`, '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    await deleteShopCouponApi(row.id)
    ElMessage.success('删除成功')
    getList()
  }).catch(() => { })
}

/** 批量删除按钮操作 */
const handleBatchDelete = () => {
  ElMessageBox.confirm(`确认删除选中的 ${selectedIds.value.length} 张优惠券吗？`, '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    await deleteShopCouponApi(selectedIds.value)
    ElMessage.success('删除成功')
    selectedIds.value = []
    getList()
  }).catch(() => { })
}

/**
 * 操作列按钮分发（TableMoreActions 只抛一个 command 事件，动作认 action.command.type，同门店培训那页）
 */
const handleActionCommand = (action: any) => {
  const { type, row } = action.command
  switch (type) {
    case 'detail':
      handleDetail(row)
      break
    case 'record':
      handleRecord(row)
      break
    case 'update':
      handleUpdate(row)
      break
    case 'delete':
      handleDelete(row)
      break
  }
}

/** 分页 */
const handleSizeChange = () => {
  queryParams.pageNum = 1
  getList()
}
const handleCurrentChange = () => {
  getList()
}

onMounted(() => {
  getList()
})
</script>

<style scoped lang="scss">
/* 参照部门管理：筛选项等分整行，控件宽度统一由 CSS 控制 */
.search-form {
  display: flex;
  flex-wrap: wrap;
  align-items: center;

  :deep(.el-form-item) {
    flex: 1 1 0;
    min-width: 180px;
    margin-right: 16px;

    &:last-child {
      flex: 0 0 auto;
      min-width: 0;
      margin-right: 0;
    }
  }

  :deep(.el-form-item .el-form-item__content) {
    min-width: 0;
  }

  /* global.scss 里对 .el-input/.el-select 写死了 240px，特异性更高，只能用 !important 覆盖 */
  :deep(.el-form-item .el-input),
  :deep(.el-form-item .el-select) {
    width: 100% !important;
  }
}

/* 时间范围那一行（照抄活动、门店培训的 .search-row）：wrapper 独占整行把日期格顶到第二行，
   里面的格子不等分、按控件自身宽度从左排；上面 :deep(.el-form-item) 把格子压成 flex: 1 1 0，
   所以这里要多带一层 .search-row 才顶得住 */
.search-form :deep(.search-row) {
  flex: 0 0 100%;
  display: flex;
  flex-wrap: nowrap;
  align-items: center;

  .el-form-item {
    flex: 0 0 auto;
    min-width: 0;
  }

  /* 日期框不跟着整行撑满：EP 给 .el-date-editor 定的宽度比行内 style 还硬，
     所以 width 之外再压一道 max-width + flex-grow:0。360px 够放"开始日期 至 结束日期"，要调宽窄改同一条规则里的两个 360 */
  .el-date-editor {
    width: 360px !important;
    max-width: 360px !important;
    flex-grow: 0 !important;
  }

  /* 按钮紧贴日期框 */
  .el-form-item.search-actions {
    margin-right: 0;
  }
}

/* 发放记录弹窗里表格上面那一排：额度汇总在左、状态筛选和「发放」那颗按钮在右（同一排，表格自带分页在下面） */
.record-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;

  .record-summary {
    color: var(--el-text-color-secondary);
  }

  .record-controls {
    display: flex;
    align-items: center;
    gap: 8px;
  }

  /* 这一格不跟着整行撑满：160px 够放"全部状态"和几个选项名 */
  .record-filter {
    width: 160px;
    flex: 0 0 auto;
  }

  .record-give {
    flex: 0 0 auto;
  }
}

/* 已选那一排（发放弹窗的用户、表单里的商品/分类范围值）：选择器是单选的，攒出来的人/条目在这儿一行行铺开，点多长都行 */
.user-chips,
.scope-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  width: 100%;

  .el-tag {
    margin: 0;
  }
}

.chips-empty {
  color: var(--el-text-color-placeholder);
}

/* 提交前那行说明（发几张、还剩多少量）：颜色压一档，别和必填报错抢 */
.give-tip {
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

/* 「每个用户各发 N 张」紧跟在数量控件右边，说清这一格乘的是什么 */
.give-quantity-tip {
  margin-left: 12px;
}
</style>
