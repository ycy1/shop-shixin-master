<template>
  <div class="app-container">
    <!-- 搜索表单 -->
    <div class="search-wrapper">
      <el-form ref="queryFormRef" :model="queryParams" inline class="search-form">
        <el-form-item label="盲盒名称" prop="boxName">
          <el-input v-model="queryParams.boxName" placeholder="请输入盲盒名称" clearable @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="潮玩系列" prop="trendyId">
          <el-select v-model="queryParams.trendyId" placeholder="全部系列" clearable filterable>
            <el-option v-for="item in trendyOptions" :key="item.id" :label="item.trendyName" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="所属门店" prop="storeId">
          <el-select v-model="queryParams.storeId" placeholder="全部门店" clearable filterable>
            <el-option v-for="item in storeOptions" :key="item.id" :label="item.storeName" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="类型" prop="boxType">
          <el-select v-model="queryParams.boxType" placeholder="全部" clearable>
            <el-option v-for="item in dict.options('trendy_box_type')" :key="item.value" :label="item.label"
              :value="Number(item.value)" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-select v-model="queryParams.status" placeholder="全部" clearable>
            <el-option v-for="item in dict.options('trendy_box_status')" :key="item.value" :label="item.label"
              :value="Number(item.value)" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
          <el-button icon="Refresh" @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>
    </div>

    <el-card class="box-card">
      <template #header>
        <ButtonGroup>
          <el-button v-permission="['shop:box:add']" type="primary" plain icon="Plus" @click="handleAdd">新增</el-button>
          <el-button v-permission="['shop:box:delete']" type="danger" plain icon="Delete"
            :disabled="selectedIds.length === 0" @click="handleBatchDelete">批量删除</el-button>
        </ButtonGroup>
      </template>

      <el-table v-loading="loading" :data="dataList" show-overflow-tooltip @selection-change="handleSelectionChange">
        <el-table-column type="selection" width="55" align="center" />
        <el-table-column label="封面" align="center" width="80">
          <template #default="{ row }">
            <el-image v-if="row.coverImage" :src="row.coverImage" :preview-src-list="[row.coverImage]"
              preview-teleported fit="cover" style="width: 44px; height: 44px; border-radius: 4px" />
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="盲盒编号" prop="boxNo" align="center" min-width="170" />
        <el-table-column label="盲盒名称" prop="boxName" align="center" min-width="150" />
        <el-table-column label="潮玩系列" align="center" width="130">
          <template #default="{ row }">
            <span>{{ row.trendyName || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="所属门店" align="center" width="130">
          <template #default="{ row }">
            <!-- 这一格为空就是全平台（表里 store_id 为 NULL），不是数据没填 -->
            <span>{{ row.storeName || '全平台' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="类型" align="center" width="90">
          <template #default="{ row }">
            <span>{{ dict.label('trendy_box_type', row.boxType) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="单抽价" align="center" width="90">
          <template #default="{ row }">
            <span>{{ money(row.price) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="已抽 / 总数" align="center" width="110">
          <template #default="{ row }">
            <span>{{ row.drawnCount || 0 }} / {{ row.totalCount ? row.totalCount : '不限' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="款式" align="center" width="80">
          <template #default="{ row }">
            <span>{{ row.itemCount || 0 }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" align="center" width="90">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.status)" size="small">{{ dict.label('trendy_box_status', row.status) }}</el-tag>
          </template>
        </el-table-column>
        <!-- 热门：勾上的排在这一列最前面，潮玩专区首页那两台机台取的就是头两台 -->
        <el-table-column label="热门" align="center" width="70">
          <template #default="{ row }">
            <el-tag v-if="row.isHot === 1" type="danger" size="small">热门</el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" align="center" width="260" fixed="right">
          <template #default="{ row }">
            <!-- 公共「更多」组件：4 条收成 2 直出 + 下拉。权限这里用 hidden（整条不给看），口径同优惠券列表 -->
            <TableMoreActions :actions="[
              { label: '盲盒商品', icon: 'Box', hidden: !hasPermission('shop:box:update'), command: { type: 'items', row } },
              { label: '抽取记录', icon: 'Tickets', command: { type: 'records', row } },
              { label: '修改', icon: 'Edit', hidden: !hasPermission('shop:box:update'), command: { type: 'update', row } },
              { label: '删除', type: 'danger', icon: 'Delete', hidden: !hasPermission('shop:box:delete'), command: { type: 'delete', row } }
            ]" @command="handleActionCommand" />
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-container">
        <el-pagination background v-model:current-page="queryParams.pageNum" v-model:page-size="queryParams.pageSize"
          :page-sizes="[10, 20, 30, 50]" :total="total" layout="total, sizes, prev, pager, next, jumper"
          @size-change="handleSizeChange" @current-change="getList" />
      </div>

      <!-- 盲盒本身的新增/修改 -->
      <el-dialog v-model="open" :title="title" width="900px" append-to-body destroy-on-close>
        <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="盲盒名称" prop="boxName">
                <el-input v-model="form.boxName" placeholder="请输入盲盒名称" maxlength="200" show-word-limit />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="盲盒类型" prop="boxType">
                <el-select v-model="form.boxType" placeholder="请选择" style="width: 100%">
                  <el-option v-for="item in dict.options('trendy_box_type')" :key="item.value" :label="item.label"
                    :value="Number(item.value)" />
                </el-select>
              </el-form-item>
            </el-col>
          </el-row>
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="潮玩系列" prop="trendyId">
                <el-select v-model="form.trendyId" placeholder="不归属某个系列" clearable filterable style="width: 100%">
                  <el-option v-for="item in trendyOptions" :key="item.id" :label="item.trendyName" :value="item.id" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="所属门店" prop="storeId">
                <el-select v-model="form.storeId" placeholder="全平台（不限门店）" clearable filterable style="width: 100%">
                  <el-option v-for="item in storeOptions" :key="item.id" :label="item.storeName" :value="item.id" />
                </el-select>
              </el-form-item>
            </el-col>
          </el-row>
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="负责运营" prop="userId">
                <user-select v-model="form.userId" :label="form.userName" placeholder="选填" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="状态" prop="status">
                <el-select v-model="form.status" placeholder="请选择" style="width: 100%">
                  <el-option v-for="item in dict.options('trendy_box_status')" :key="item.value" :label="item.label"
                    :value="Number(item.value)" />
                </el-select>
              </el-form-item>
            </el-col>
          </el-row>
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="单抽价格" prop="price">
                <el-input-number v-model="form.price" :min="0.01" :precision="2" :step="1" style="width: 100%" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="端盒价格" prop="endBoxPrice">
                <el-input-number v-model="form.endBoxPrice" :min="0.01" :precision="2" :step="1" style="width: 100%"
                  placeholder="整盒买的价格，不能低于单抽价" />
              </el-form-item>
            </el-col>
          </el-row>
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="每次抽取数" prop="drawCount">
                <el-input-number v-model="form.drawCount" :min="1" style="width: 100%" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="最大抽取次数" prop="maxDrawCount">
                <el-input-number v-model="form.maxDrawCount" :min="1" style="width: 100%" placeholder="不填=不限" />
              </el-form-item>
            </el-col>
          </el-row>
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="总可抽次数" prop="totalCount">
                <el-input-number v-model="form.totalCount" :min="0" style="width: 100%" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="已抽取次数">
                <!-- 抽奖那侧维护的计数，这里只给看：表单改了也不算，服务层那一列是 NEVER -->
                <el-input :model-value="form.drawnCount || 0" disabled />
              </el-form-item>
            </el-col>
          </el-row>
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="隐藏款概率" prop="hiddenRate">
                <el-input-number v-model="form.hiddenRate" :min="0" :max="1" :precision="4" :step="0.0001"
                  style="width: 100%" placeholder="0.0139 = 1.39%" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="保底次数" prop="guaranteeCount">
                <el-input-number v-model="form.guaranteeCount" :min="1" style="width: 100%" placeholder="不填=无保底" />
              </el-form-item>
            </el-col>
          </el-row>
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="公示概率" prop="isProbabilityShow">
                <el-switch v-model="form.isProbabilityShow" :active-value="1" :inactive-value="0" />
                <span class="form-tip">合规要求：对外要能看抽中概率</span>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="排序" prop="sort">
                <el-input-number v-model="form.sort" :min="0" style="width: 100%" />
              </el-form-item>
            </el-col>
          </el-row>
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="热门" prop="isHot">
                <el-switch v-model="form.isHot" :active-value="1" :inactive-value="0" />
                <span class="form-tip">勾上的排最前，潮玩专区首页那两台机台取的就是头两台</span>
              </el-form-item>
            </el-col>
          </el-row>
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="开始时间" prop="startTime">
                <el-date-picker v-model="form.startTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss"
                  placeholder="选填" style="width: 100%" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="结束时间" prop="endTime">
                <el-date-picker v-model="form.endTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss"
                  placeholder="选填" style="width: 100%" />
              </el-form-item>
            </el-col>
          </el-row>
          <el-form-item label="盲盒封面" prop="coverImage">
            <upload-image v-model="form.coverImage" :size="120" source="trendyBox" />
          </el-form-item>
          <el-form-item label="玩法说明" prop="description">
            <el-input v-model="form.description" type="textarea" :rows="4" placeholder="规则、兑换说明等，选填" />
          </el-form-item>
        </el-form>
        <template #footer>
          <div class="dialog-footer">
            <el-button @click="cancel">取 消</el-button>
            <el-button type="primary" :loading="submitLoading" @click="submitForm">确 定</el-button>
          </div>
        </template>
      </el-dialog>

      <!-- 盲盒商品（款式）：从操作栏那棵「盲盒商品」进来 -->
      <el-dialog v-model="itemsOpen" width="1100px" append-to-body destroy-on-close>
        <template #header>
          <span class="el-dialog__title">盲盒商品{{ itemsTitle ? ` - ${itemsTitle}` : '' }}</span>
        </template>
        <ButtonGroup class="table-toolbar">
          <el-button v-permission="['shop:box:update']" type="primary" plain icon="Plus"
            @click="handleItemAdd">新增款式</el-button>
          <el-button v-permission="['shop:box:update']" type="danger" plain icon="Delete"
            :disabled="checkedItemIds.length === 0" @click="handleItemBatchDelete">批量删除</el-button>
          <span class="probability-tip">概率按小数存（0.0139 = 1.39%），启用中的款式合计不能超过 100%，当前合计 {{ itemSumText }}</span>
        </ButtonGroup>
        <el-table v-loading="itemsLoading" :data="itemList" row-key="id" show-overflow-tooltip max-height="420"
          @selection-change="handleItemSelectionChange">
          <el-table-column type="selection" width="45" />
          <el-table-column label="款式图" align="center" width="80">
            <template #default="{ row }">
              <el-image v-if="row.styleImage" :src="row.styleImage" :preview-src-list="[row.styleImage]"
                preview-teleported fit="cover" style="width: 44px; height: 44px; border-radius: 4px" />
              <span v-else>-</span>
            </template>
          </el-table-column>
          <el-table-column label="款式名称" prop="styleName" align="center" min-width="150" />
          <el-table-column label="隐藏款" align="center" width="80">
            <template #default="{ row }">
              <el-tag v-if="row.isHidden === 1" type="warning" size="small">隐藏</el-tag>
              <span v-else>-</span>
            </template>
          </el-table-column>
          <el-table-column label="概率" align="center" width="90">
            <template #default="{ row }">
              <span>{{ percent(row.probability) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="总 / 已抽 / 剩余" align="center" width="130">
            <template #default="{ row }">
              <span>{{ row.totalCount ? row.totalCount : '不限' }} / {{ row.drawnCount || 0 }} / {{ row.totalCount ? row.remainCount : '-' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="挂的规格" align="center" min-width="170">
            <template #default="{ row }">
              <span>{{ row.productName ? `${row.productName} ${row.skuText || ''}`.trim() : (row.skuId ? `规格 #${row.skuId}` : '-') }}</span>
            </template>
          </el-table-column>
          <el-table-column label="状态" align="center" width="80">
            <template #default="{ row }">
              <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">{{ row.status === 1 ? '启用' : '禁用' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="排序" prop="sort" align="center" width="70" />
          <el-table-column label="操作" align="center" width="180" fixed="right">
            <template #default="scope">
              <el-button v-permission="['shop:box:update']" type="primary" link icon="Edit"
                @click="handleItemUpdate(scope.row)">修改</el-button>
              <el-button v-permission="['shop:box:update']" type="danger" link icon="Delete"
                @click="handleItemDelete(scope.row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <template #footer>
          <div class="dialog-footer">
            <el-button @click="itemsOpen = false">关 闭</el-button>
          </div>
        </template>
      </el-dialog>

      <!-- 款式的新增/修改 -->
      <el-dialog v-model="itemOpen" :title="itemTitle" width="720px" append-to-body destroy-on-close>
        <el-form ref="itemFormRef" :model="itemForm" :rules="itemRules" label-width="100px">
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="款式名称" prop="styleName">
                <el-input v-model="itemForm.styleName" placeholder="如「隐藏款-星空」" maxlength="100" show-word-limit />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="是否隐藏款" prop="isHidden">
                <el-switch v-model="itemForm.isHidden" :active-value="1" :inactive-value="0" />
              </el-form-item>
            </el-col>
          </el-row>
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="概率" prop="probability">
                <el-input-number v-model="itemForm.probability" :min="0.0001" :max="1" :precision="4" :step="0.0001"
                  style="width: 100%" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="数量" prop="totalCount">
                <el-input-number v-model="itemForm.totalCount" :min="0" style="width: 100%" />
              </el-form-item>
            </el-col>
          </el-row>
          <el-form-item label="挂的规格" prop="skuId">
            <!-- 抽中这一款要发出去的那条规格，必填：不挂规格就没法发货，抽盒记录那边也没 sku 可对。
                 盒挂了潮玩系列时只能在本系列的商品里挑（那格下拉由这个 ref 收窄） -->
            <shop-product-sku-select v-model="itemForm.skuId" :label="itemSkuLabel"
              :trendy-id="currentTrendyId" placeholder="请选择要发的规格" />
            <span v-if="currentTrendyId" class="form-tip">本盲盒归属「{{ currentTrendyName }}」，只能挑该系列下的规格</span>
          </el-form-item>
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="排序" prop="sort">
                <el-input-number v-model="itemForm.sort" :min="0" style="width: 100%" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="状态" prop="status">
                <el-radio-group v-model="itemForm.status">
                  <el-radio :value="1">启用</el-radio>
                  <el-radio :value="0">禁用</el-radio>
                </el-radio-group>
              </el-form-item>
            </el-col>
          </el-row>
          <el-form-item label="款式图" prop="styleImage">
            <upload-image v-model="itemForm.styleImage" :size="120" source="trendyBoxItem" />
          </el-form-item>
          <el-form-item label="备注" prop="remark">
            <el-input v-model="itemForm.remark" type="textarea" :rows="2" placeholder="选填" maxlength="255"
              show-word-limit />
          </el-form-item>
        </el-form>
        <template #footer>
          <div class="dialog-footer">
            <el-button @click="itemOpen = false">取 消</el-button>
            <el-button type="primary" :loading="itemSubmitLoading" @click="submitItemForm">确 定</el-button>
          </div>
        </template>
      </el-dialog>
      <!-- 抽取记录：只读流水。款式那三格是抽中当时的快照，款式后来改了名也不影响这里显示 -->
      <el-dialog v-model="recordsOpen" width="900px" append-to-body destroy-on-close>
        <template #header>
          <span class="el-dialog__title">抽取记录{{ recordsTitle ? ` - ${recordsTitle}` : '' }}</span>
        </template>
        <el-form :model="recordQuery" inline class="search-form">
          <el-form-item label="款式">
            <el-select v-model="recordQuery.isHidden" placeholder="全部" clearable @change="handleRecordQuery">
              <el-option label="普通款" :value="0" />
              <el-option label="隐藏款" :value="1" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" icon="Search" @click="handleRecordQuery">搜索</el-button>
            <el-button icon="Refresh" @click="resetRecordQuery">重置</el-button>
          </el-form-item>
        </el-form>
        <el-table v-loading="recordsLoading" :data="recordList" show-overflow-tooltip max-height="420">
          <el-table-column label="款式图" align="center" width="80">
            <template #default="{ row }">
              <el-image v-if="row.styleImage" :src="row.styleImage" :preview-src-list="[row.styleImage]"
                preview-teleported fit="cover" style="width: 44px; height: 44px; border-radius: 4px" />
              <span v-else>-</span>
            </template>
          </el-table-column>
          <el-table-column label="款式名称" prop="styleName" align="center" min-width="150">
            <template #default="{ row }">
              <span>{{ row.styleName || '-' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="隐藏款" align="center" width="80">
            <template #default="{ row }">
              <el-tag v-if="row.isHidden === 1" type="warning" size="small">隐藏</el-tag>
              <span v-else>-</span>
            </template>
          </el-table-column>
          <el-table-column label="抽取用户" align="center" min-width="130">
            <template #default="{ row }">
              <span>{{ row.userName || `用户 #${row.userId}` }}</span>
            </template>
          </el-table-column>
          <el-table-column label="挂的规格" align="center" width="110">
            <template #default="{ row }">
              <span>{{ row.skuId ? `#${row.skuId}` : '未挂' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="抽取时间" align="center" width="170">
            <template #default="{ row }">
              <span>{{ row.createTime || '-' }}</span>
            </template>
          </el-table-column>
        </el-table>
        <div class="pagination-container">
          <el-pagination background v-model:current-page="recordQuery.pageNum" v-model:page-size="recordQuery.pageSize"
            :page-sizes="[10, 20, 30, 50]" :total="recordTotal" layout="total, sizes, prev, pager, next, jumper"
            @size-change="handleRecordQuery" @current-change="getRecords" />
        </div>
        <template #footer>
          <div class="dialog-footer">
            <el-button @click="recordsOpen = false">关 闭</el-button>
          </div>
        </template>
      </el-dialog>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import {
  listShopBoxApi,
  detailShopBoxApi,
  addShopBoxApi,
  updateShopBoxApi,
  deleteShopBoxApi,
  listBoxItemsApi,
  addBoxItemApi,
  updateBoxItemApi,
  deleteBoxItemApi,
  listBoxRecordsApi
} from '@/api/trendy/box'
import { optionsShopTrendyApi } from '@/api/trendy/trendy'
import { optionsShopStoreApi } from '@/api/store/store'
import ButtonGroup from '@/components/ButtonGroup/index.vue'
import TableMoreActions from '@/components/TableMoreActions/index.vue'
import UploadImage from '@/components/Upload/Image.vue'
import UserSelect from '@/components/UserSelect/index.vue'
import ShopProductSkuSelect from '@/components/ShopProductSkuSelect/index.vue'
import { useDict } from '@/utils/dictCache'
import { useUserStore } from '@/store/modules/user'

const dict = useDict('trendy_box_type', 'trendy_box_status')

const userStore = useUserStore()
const permissions = computed(() => userStore.user.permissions || [])

/** 操作列交给公共「更多」组件渲染，权限得在这里自己判（组件只认 actions 上的 hidden） */
const hasPermission = (permission: string): boolean => permissions.value.includes(permission)

const loading = ref(true)
const submitLoading = ref(false)
const total = ref(0)
const dataList = ref<any[]>([])
const selectedIds = ref<any[]>([])
const title = ref('')
const open = ref(false)

const queryFormRef = ref<FormInstance>()
const formRef = ref<FormInstance>()

const queryParams = reactive<any>({
  pageNum: 1,
  pageSize: 10,
  boxName: undefined,
  trendyId: undefined,
  storeId: undefined,
  boxType: undefined,
  status: undefined
})

const form = reactive<any>({})

/** 系列和门店两份候选：搜索栏、表单两处共用，进页面拉一次 */
const trendyOptions = ref<any[]>([])
const storeOptions = ref<any[]>([])

const rules = reactive<FormRules>({
  boxName: [{ required: true, message: '盲盒名称不能为空', trigger: 'blur' }],
  boxType: [{ required: true, message: '请选择盲盒类型', trigger: 'change' }],
  price: [{ required: true, message: '单抽价格不能为空', trigger: 'blur' }],
  endBoxPrice: [{ required: true, message: '端盒价格不能为空', trigger: 'blur' }],
  status: [{ required: true, message: '请选择状态', trigger: 'change' }]
})

const money = (value: any) => (value == null || value === '' ? '-' : `￥${Number(value).toFixed(2)}`)

/** 概率存的是小数（0.0139），页面上说百分比 */
const percent = (value: any) => {
  if (value == null || value === '') return '-'
  return `${(Number(value) * 100).toFixed(2).replace(/\.?0+$/, '')}%`
}

const statusTag = (status: number) => {
  // 1未开始 2进行中 3已结束 4已取消
  return ({ 2: 'success', 3: 'info', 4: 'danger' } as Record<number, string>)[status] || 'primary'
}

const getList = async () => {
  loading.value = true
  try {
    const { data } = await listShopBoxApi(queryParams)
    dataList.value = data.records || []
    total.value = data.total || 0
  } catch (error) {
  } finally {
    loading.value = false
  }
}

const getOptions = async () => {
  const fill = async (p: Promise<any>, put: (v: any[]) => void) => {
    try {
      const { data } = await p
      put(data || [])
    } catch (error) {
    }
  }
  await Promise.all([
    fill(optionsShopTrendyApi(), v => (trendyOptions.value = v)),
    fill(optionsShopStoreApi(), v => (storeOptions.value = v))
  ])
}

const reset = () => {
  Object.assign(form, {
    id: undefined,
    boxNo: undefined,
    boxName: undefined,
    trendyId: undefined,
    storeId: undefined,
    userId: undefined,
    userName: undefined,
    boxType: 1,
    price: undefined,
    endBoxPrice: undefined,
    drawCount: 1,
    maxDrawCount: undefined,
    hiddenRate: undefined,
    guaranteeCount: undefined,
    isProbabilityShow: 1,
    totalCount: 0,
    drawnCount: 0,
    coverImage: undefined,
    description: undefined,
    startTime: undefined,
    endTime: undefined,
    status: 1,
    isHot: 0,
    sort: 0
  })
  formRef.value?.resetFields()
}

const cancel = () => {
  open.value = false
  reset()
}

const handleQuery = () => {
  queryParams.pageNum = 1
  getList()
}

const resetQuery = () => {
  queryFormRef.value?.resetFields()
  Object.assign(queryParams, { boxName: undefined, trendyId: undefined, storeId: undefined, boxType: undefined, status: undefined })
  handleQuery()
}

const handleSelectionChange = (selection: { id: any }[]) => {
  selectedIds.value = selection.map(item => item.id)
}

const handleSizeChange = () => {
  queryParams.pageNum = 1
  getList()
}

const handleAdd = () => {
  reset()
  open.value = true
  title.value = '添加盲盒'
}

const handleUpdate = async (row: any) => {
  reset()
  try {
    const { data } = await detailShopBoxApi(row.id)
    Object.assign(form, data)
    // 详情那条走 getById，不带回填的用户名（它是非本表列），所以拿列表那一行已经有的名字顶着，
    // 否则修改弹窗里「负责运营」那格只剩一串 id
    if (!form.userName) form.userName = row.userName
    open.value = true
    title.value = '修改盲盒'
  } catch (error) {
  }
}

const submitForm = () => {
  formRef.value?.validate(async (valid: any) => {
    if (!valid) return
    submitLoading.value = true
    try {
      if (form.id !== undefined) {
        await updateShopBoxApi(form)
        ElMessage.success('修改成功')
      } else {
        await addShopBoxApi(form)
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

const handleDelete = (row: any) => {
  const 款数 = row.itemCount ? `，连同它名下 ${row.itemCount} 个款式一起删除` : ''
  ElMessageBox.confirm(`确认删除盲盒「${row.boxName}」吗${款数}？`, '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    await deleteShopBoxApi(row.id)
    ElMessage.success('删除成功')
    getList()
  }).catch(() => { })
}

const handleBatchDelete = () => {
  ElMessageBox.confirm(`确认删除选中的 ${selectedIds.value.length} 只盲盒吗？它们名下的款式会一起删除。`, '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    await deleteShopBoxApi(selectedIds.value)
    ElMessage.success('删除成功')
    selectedIds.value = []
    getList()
  }).catch(() => { })
}

/* ---------------- 盲盒商品（款式） ---------------- */

const itemsOpen = ref(false)
const itemsLoading = ref(false)
const itemsTitle = ref('')
const currentBoxId = ref<number>()
/**
 * 当前这只盒挂的潮玩系列：款式只能挑这个系列下的商品规格，所以这一格要一路带到规格弹窗里当筛选。
 * 名字只用来在表单上说明这次的收窄口径，没挂系列时那格不显示。
 */
const currentTrendyId = ref<number | string>()
const currentTrendyName = ref('')
const itemList = ref<any[]>([])
const checkedItemIds = ref<any[]>([])

const itemOpen = ref(false)
const itemTitle = ref('')
const itemLoading = ref(false)
const itemSubmitLoading = ref(false)
const itemFormRef = ref<FormInstance>()
const itemForm = reactive<any>({})
/** 款式表单里 SKU 那格的回显文字：详情接口不带名字，只能拿列表那一行的回填值顶着 */
const itemSkuLabel = ref('')

const itemRules = reactive<FormRules>({
  styleName: [{ required: true, message: '款式名称不能为空', trigger: 'blur' }],
  probability: [{ required: true, message: '概率不能为空', trigger: 'blur' }],
  skuId: [{ required: true, message: '请选择要发的规格', trigger: 'change' }]
})

/** 底部那句「当前合计」：只算启用中的，和后端那道校验同一个口径 */
const itemSumText = computed(() => {
  const sum = itemList.value
    .filter((item: any) => item.status === 1)
    .reduce((acc: number, item: any) => acc + Number(item.probability || 0), 0)
  return `${(sum * 100).toFixed(2).replace(/\.?0+$/, '')}%`
})

const getItems = async () => {
  if (!currentBoxId.value) return
  itemsLoading.value = true
  try {
    const { data } = await listBoxItemsApi(currentBoxId.value)
    itemList.value = data || []
  } catch (error) {
  } finally {
    itemsLoading.value = false
  }
}

const openItems = (row: any) => {
  currentBoxId.value = row.id
  currentTrendyId.value = row.trendyId
  currentTrendyName.value = row.trendyName || ''
  itemsTitle.value = row.boxName
  checkedItemIds.value = []
  itemList.value = []
  itemsOpen.value = true
  getItems()
}

const handleItemSelectionChange = (selection: { id: any }[]) => {
  checkedItemIds.value = selection.map(item => item.id)
}

const resetItem = () => {
  Object.assign(itemForm, {
    id: undefined,
    boxId: currentBoxId.value,
    skuId: undefined,
    styleName: undefined,
    styleImage: undefined,
    isHidden: 0,
    probability: undefined,
    totalCount: 0,
    sort: 0,
    status: 1,
    remark: undefined
  })
  itemFormRef.value?.resetFields()
}

const handleItemAdd = () => {
  resetItem()
  itemSkuLabel.value = ''
  itemOpen.value = true
  itemTitle.value = '添加款式'
}

const handleItemUpdate = (row: any) => {
  resetItem()
  Object.assign(itemForm, row)
  itemSkuLabel.value = row.productName ? `${row.productName} ${row.skuText || ''}`.trim() : ''
  itemOpen.value = true
  itemTitle.value = '修改款式'
}

const submitItemForm = () => {
  itemFormRef.value?.validate(async (valid: any) => {
    if (!valid) return
    itemSubmitLoading.value = true
    try {
      const payload = { ...itemForm, boxId: currentBoxId.value }
      if (payload.id !== undefined) {
        await updateBoxItemApi(payload)
        ElMessage.success('修改成功')
      } else {
        await addBoxItemApi(payload)
        ElMessage.success('新增成功')
      }
      itemOpen.value = false
      await getItems()
      // 款式数变了，外面那一列「款式」跟着重拉
      getList()
    } catch (error) {
    } finally {
      itemSubmitLoading.value = false
    }
  })
}

const handleItemDelete = (row: any) => {
  ElMessageBox.confirm(`确认删除款式「${row.styleName}」吗？`, '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    await deleteBoxItemApi(row.id)
    ElMessage.success('删除成功')
    await getItems()
    getList()
  }).catch(() => { })
}

const handleItemBatchDelete = () => {
  ElMessageBox.confirm(`确认删除选中的 ${checkedItemIds.value.length} 个款式吗？`, '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    await deleteBoxItemApi(checkedItemIds.value)
    ElMessage.success('删除成功')
    checkedItemIds.value = []
    await getItems()
    getList()
  }).catch(() => { })
}

/* ---------------- 抽取记录（只读流水） ---------------- */

const recordsOpen = ref(false)
const recordsLoading = ref(false)
const recordsTitle = ref('')
const recordList = ref<any[]>([])
const recordTotal = ref(0)
const recordQuery = reactive<any>({
  pageNum: 1,
  pageSize: 10,
  boxId: undefined,
  isHidden: undefined
})

const getRecords = async () => {
  recordsLoading.value = true
  try {
    const { data } = await listBoxRecordsApi(recordQuery)
    recordList.value = data.records || []
    recordTotal.value = data.total || 0
  } catch (error) {
  } finally {
    recordsLoading.value = false
  }
}

const openRecords = (row: any) => {
  recordsTitle.value = row.boxName
  recordQuery.boxId = row.id
  recordQuery.isHidden = undefined
  recordList.value = []
  recordTotal.value = 0
  recordsOpen.value = true
  handleRecordQuery()
}

const handleRecordQuery = () => {
  recordQuery.pageNum = 1
  getRecords()
}

const resetRecordQuery = () => {
  recordQuery.isHidden = undefined
  handleRecordQuery()
}

/** 「更多」组件统一抛 command，按 type 分发到原来那几颗按钮 */
const handleActionCommand = (action: any) => {
  const { type, row } = action.command
  switch (type) {
    case 'items':
      openItems(row)
      break
    case 'records':
      openRecords(row)
      break
    case 'update':
      handleUpdate(row)
      break
    case 'delete':
      handleDelete(row)
      break
  }
}

onMounted(() => {
  getList()
  getOptions()
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
    min-width: 170px;
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
  :deep(.el-form-item .el-select),
  :deep(.el-form-item .el-tree-select) {
    width: 100% !important;
  }
}

.table-toolbar {
  margin-bottom: 12px;
}

.form-tip {
  margin-left: 12px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.probability-tip {
  margin-left: 16px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
</style>
