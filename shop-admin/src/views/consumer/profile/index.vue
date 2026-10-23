<template>
  <div class="app-container">
    <!--
      个人信息：一个账号看自己名下的八样东西（基础信息 / 订单 / 优惠券 / 徽章 / 积分 / 钱包 / 地址 / 广场消息）。
      八块数据全走别的页面已经在用的读接口，那些读接口都只给消费者账号钉登录 id ——
      所以平台账号坐这一页时，徽章、钱包和广场消息那三条要把自己的 id 递过去（底下那几步 loadTab('base') 就是为它）。
      下单在小程序侧、券和徽章由运营发放、积分只能由积分动作改、钱只能由下单冻结和退款退回改，
      所以这几块在这一页是只读的。
      三个写入口：基础信息那一格的「修改」（只改自己那几格，走 /sys/user/myProfile）、
      地址那一格的新增/修改/删除（走收货地址那页同一套接口），和卡片顶上「签到记录」那颗 ——
      都是这个人自己的动作。
    -->
    <el-card class="box-card">
      <div class="page-toolbar">
        <el-button type="primary" icon="Calendar" @click="handleSignin">签到记录</el-button>
        <span class="page-tip">一天签一次，签下来的分直接进积分账户</span>
      </div>

      <el-tabs v-model="activeTab" @tab-change="handleTabChange">
        <!-- 基础信息：一次接口（/sys/user/profile，服务端按登录 id 查），不做分页 -->
        <el-tab-pane label="基础信息" name="base">
          <div v-loading="base.loading" class="pane">
            <div class="pane-toolbar">
              <el-button type="primary" plain icon="Edit" @click="handleBaseUpdate">修改</el-button>
              <span class="pane-tip">只能改自己这几格；账号、账号类型、状态、角色由平台那边定，这一页不动</span>
            </div>
            <el-descriptions :column="2" border>
              <el-descriptions-item label="头像">
                <!-- 不用 el-avatar：它只画缩略图、点开没反应。照送货员「我的信息」那格换成 el-image，
                     preview-teleported 要带上，否则放大层会被 tab 内容的层叠上下文盖住；
                     缩略图保持原来那颗的圆形（overflow:hidden 是必需的，el-image 里面那张 img 自己不带圆角） -->
                <el-image v-if="base.data.avatar" :src="base.data.avatar" :preview-src-list="[base.data.avatar]"
                  preview-teleported fit="cover"
                  style="width: 56px; height: 56px; border-radius: 50%; overflow: hidden" />
                <span v-else>-</span>
              </el-descriptions-item>
              <el-descriptions-item label="昵称">{{ base.data.nickname || '-' }}</el-descriptions-item>
              <el-descriptions-item label="账号">{{ base.data.username || '-' }}</el-descriptions-item>
              <el-descriptions-item label="手机号">{{ base.data.mobile || '-' }}</el-descriptions-item>
              <el-descriptions-item label="邮箱">{{ base.data.email || '-' }}</el-descriptions-item>
              <!-- 性别和状态两格照抄用户管理那页：sys_user 上这两列本来就没有字典 -->
              <el-descriptions-item label="性别">{{ sexText(base.data.sex) }}</el-descriptions-item>
              <el-descriptions-item label="账号类型">
                {{ base.data.accountType == null ? '-' : dict.label('account_type', base.data.accountType) }}
              </el-descriptions-item>
              <el-descriptions-item label="状态">
                <el-tag v-if="base.data.status != null" size="small"
                  :type="base.data.status === 1 ? 'success' : 'danger'">
                  {{ base.data.status === 1 ? '启用' : '禁用' }}
                </el-tag>
                <span v-else>-</span>
              </el-descriptions-item>
              <el-descriptions-item label="所在地区">{{ base.data.areaZh || '-' }}</el-descriptions-item>
              <el-descriptions-item label="详细地址">{{ base.data.address || '-' }}</el-descriptions-item>
              <el-descriptions-item label="个性签名" :span="2">{{ base.data.signature || '-' }}</el-descriptions-item>
              <el-descriptions-item label="角色">{{ (base.data.roles || []).join('、') || '-' }}</el-descriptions-item>
              <el-descriptions-item label="注册时间">
                {{ validate.formatTime(base.data.createTime) || '-' }}
              </el-descriptions-item>
              <el-descriptions-item label="最后登录时间">
                {{ validate.formatTime(base.data.lastLoginTime) || '-' }}
              </el-descriptions-item>
            </el-descriptions>
          </div>
        </el-tab-pane>

        <!-- 订单：一行一张订单（和「我的订单」那页同一个接口、同一套列），这里只看不操作 -->
        <el-tab-pane label="订单" name="order">
          <div class="pane">
            <div class="pane-toolbar">
              <el-select v-model="order.query.status" placeholder="全部状态" clearable class="pane-filter"
                @change="handleOrderQuery">
                <el-option v-for="item in dict.options(ORDER_DICT)" :key="item.value" :label="item.label"
                  :value="item.value" />
              </el-select>
            </div>
            <el-table v-loading="order.loading" :data="order.rows" size="small" border show-overflow-tooltip
              empty-text="还没有下过单">
              <el-table-column label="订单号" prop="orderNo" align="center" min-width="190" />
              <el-table-column label="商品" align="center" width="130">
                <template #default="{ row }">共 {{ row.itemCount || 0 }} 行 · {{ row.totalQuantity || 0 }} 件</template>
              </el-table-column>
              <el-table-column label="订单状态" align="center" width="100">
                <template #default="{ row }">
                  <el-tag size="small" :type="dict.tag(ORDER_DICT, row.status)">
                    {{ dict.label(ORDER_DICT, row.status) }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="订单金额" align="right" width="100">
                <template #default="{ row }">{{ priceText(row.totalAmount) }}</template>
              </el-table-column>
              <el-table-column label="实付金额" align="right" width="100">
                <template #default="{ row }">
                  <span class="pay-amount">{{ priceText(row.payAmount) }}</span>
                </template>
              </el-table-column>
              <el-table-column label="支付时间" align="center" width="150">
                <template #default="{ row }">
                  {{ row.payTime ? validate.formatTime(row.payTime, 'YYYY-MM-DD HH:mm') : '未支付' }}
                </template>
              </el-table-column>
              <el-table-column label="下单时间" align="center" width="150">
                <template #default="{ row }">{{ validate.formatTime(row.createTime, 'YYYY-MM-DD HH:mm') || '-' }}</template>
              </el-table-column>
            </el-table>
            <div class="pagination-container">
              <el-pagination background size="small" v-model:current-page="order.query.pageNum"
                v-model:page-size="order.query.pageSize" :page-sizes="[10, 20, 50]" :total="order.total"
                layout="total, sizes, prev, pager, next, jumper" @size-change="handleOrderSizeChange"
                @current-change="getOrderList" />
            </div>
          </div>
        </el-tab-pane>

        <!-- 优惠券：自己名下那几张券（读的是用户券那张表，面额是发到手那一刻定格的快照，不跟着模板改） -->
        <el-tab-pane label="优惠券" name="coupon">
          <div class="pane">
            <div class="pane-toolbar">
              <el-select v-model="coupon.query.status" placeholder="全部状态" clearable class="pane-filter"
                @change="handleCouponQuery">
                <el-option v-for="item in dict.options(COUPON_STATUS_DICT)" :key="item.value" :label="item.label"
                  :value="item.value" />
              </el-select>
            </div>
            <el-table v-loading="coupon.loading" :data="coupon.rows" size="small" border show-overflow-tooltip
              empty-text="手里还没有券">
              <el-table-column label="券名" prop="couponName" align="center" min-width="140" />
              <el-table-column label="券码" prop="couponCode" align="center" min-width="150" />
              <el-table-column label="类型" align="center" width="90">
                <template #default="{ row }">
                  <el-tag size="small" :type="dict.tag(COUPON_TYPE_DICT, row.couponType)">
                    {{ dict.label(COUPON_TYPE_DICT, row.couponType) }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="优惠" align="center" min-width="150">
                <template #default="{ row }">{{ amountText(row) }}</template>
              </el-table-column>
              <el-table-column label="状态" align="center" width="90">
                <template #default="{ row }">
                  <el-tag size="small" :type="dict.tag(COUPON_STATUS_DICT, row.status)">
                    {{ dict.label(COUPON_STATUS_DICT, row.status) }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="领取方式" align="center" width="100">
                <template #default="{ row }">{{ dict.label(GET_WAY_DICT, row.getWay) }}</template>
              </el-table-column>
              <el-table-column label="领取时间" align="center" width="150">
                <template #default="{ row }">{{ validate.formatTime(row.receiveTime) || '-' }}</template>
              </el-table-column>
              <el-table-column label="过期时间" align="center" width="150">
                <template #default="{ row }">{{ validate.formatTime(row.expireTime) || '-' }}</template>
              </el-table-column>
              <el-table-column label="使用时间" align="center" width="150">
                <template #default="{ row }">{{ validate.formatTime(row.useTime) || '-' }}</template>
              </el-table-column>
              <el-table-column label="用在哪单" align="center" min-width="180">
                <template #default="{ row }">{{ row.orderNo || '-' }}</template>
              </el-table-column>
            </el-table>
            <div class="pagination-container">
              <el-pagination background size="small" v-model:current-page="coupon.query.pageNum"
                v-model:page-size="coupon.query.pageSize" :page-sizes="[10, 20, 50]" :total="coupon.total"
                layout="total, sizes, prev, pager, next, jumper" @size-change="handleCouponSizeChange"
                @current-change="getCouponList" />
            </div>
          </div>
        </el-tab-pane>

        <!-- 徽章：名下那一枚一枚的徽章（读的是用户徽章那张表，名称/文字/图标三格是拿到那一刻定格的快照，
             后台改了徽章也不会串到已经发出去的这一枚上）。这一格只有「是否展示」能动（走的就是「徽章管理 →
             获取记录」里那一格同一个口子），发徽章、改状态、删除都还在「徽章管理」那页 -->
        <el-tab-pane label="徽章" name="badge">
          <div class="pane">
            <div class="pane-toolbar">
              <el-select v-model="badge.query.source" placeholder="全部获得方式" clearable class="pane-filter"
                @change="handleBadgeQuery">
                <el-option v-for="item in dict.options(BADGE_SOURCE_DICT)" :key="item.value" :label="item.label"
                  :value="item.value" />
              </el-select>
              <el-select v-model="badge.query.status" placeholder="全部状态" clearable class="pane-filter"
                @change="handleBadgeQuery">
                <el-option v-for="item in dict.options(BADGE_STATUS_DICT)" :key="item.value" :label="item.label"
                  :value="item.value" />
              </el-select>
              <span class="pane-tip">状态只有「有效 / 失效」两档（「已过期」那一档已下线，失效由「徽章管理 → 获取记录」里那颗开关手工置）；过期是读的时候按有效期那一格现判的，所以状态筛选项里筛不出已经过期的那些行</span>
            </div>
            <el-table v-loading="badge.loading" :data="badge.rows" size="small" border show-overflow-tooltip
              empty-text="还没有拿到过徽章">
              <el-table-column label="徽章（快照）" align="center" min-width="170" :show-overflow-tooltip="false">
                <template #default="{ row }">
                  <badge-view :icon="row.badgeIcon" :text="row.badgeText" :name="row.badgeName" preview />
                </template>
              </el-table-column>
              <el-table-column label="类型" align="center" width="90">
                <template #default="{ row }">
                  <el-tag v-if="row.badgeType != null" size="small" :type="dict.tag(BADGE_TYPE_DICT, row.badgeType)">
                    {{ dict.label(BADGE_TYPE_DICT, row.badgeType) }}
                  </el-tag>
                  <span v-else>-</span>
                </template>
              </el-table-column>
              <el-table-column label="获得方式" align="center" width="110">
                <template #default="{ row }">
                  <el-tag size="small" :type="dict.tag(BADGE_SOURCE_DICT, row.source)">
                    {{ dict.label(BADGE_SOURCE_DICT, row.source) }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="状态" align="center" width="90">
                <template #default="{ row }">
                  <el-tag size="small" :type="dict.tag(BADGE_STATUS_DICT, row.status)">
                    {{ dict.label(BADGE_STATUS_DICT, row.status) }}
                  </el-tag>
                </template>
              </el-table-column>
              <!-- 这一格是一改就提交的开关，形状照「徽章管理 → 获取记录」里那一格：只动 is_show 一列，
                   快照、有效期、状态都不碰，也不收分不收券。收起来的那枚不再挂在账号卡下面那一排，
                   但这一列不筛 —— 收起来的还得看得见才放得回来 -->
              <el-table-column label="是否展示" align="center" width="90">
                <template #default="{ row }">
                  <el-switch v-model="row.isShow" :active-value="1" :inactive-value="0"
                    :loading="badge.showLoadingId === row.id" :disabled="!hasPermission('activity:badge:recordShow')"
                    @change="handleBadgeShowChange(row)" />
                </template>
              </el-table-column>
              <el-table-column label="获得时间" align="center" width="150">
                <template #default="{ row }">{{ validate.formatTime(row.startTime) || '-' }}</template>
              </el-table-column>
              <el-table-column label="有效期到" align="center" width="150">
                <template #default="{ row }">{{ validate.formatTime(row.endTime) || '永久' }}</template>
              </el-table-column>
              <el-table-column label="来源单据" align="center" min-width="120">
                <template #default="{ row }">{{ row.sourceId || '-' }}</template>
              </el-table-column>
            </el-table>
            <div class="pagination-container">
              <el-pagination background size="small" v-model:current-page="badge.query.pageNum"
                v-model:page-size="badge.query.pageSize" :page-sizes="[10, 20, 50]" :total="badge.total"
                layout="total, sizes, prev, pager, next, jumper" @size-change="handleBadgeSizeChange"
                @current-change="getBadgeList" />
            </div>
          </div>
        </el-tab-pane>

        <!-- 积分：上面一块是账户那五个数（库里存的，不做现算），下面一张表是他这本账的进出明细 -->
        <el-tab-pane label="积分" name="points">
          <div class="pane">
            <div v-loading="points.loadingAccount" class="points-account">
              <el-descriptions v-if="points.account" :column="3" border>
                <el-descriptions-item label="可用积分">
                  <span class="balance">{{ pointsText(points.account.balance) }}</span>
                </el-descriptions-item>
                <el-descriptions-item label="累计获得">{{ pointsText(points.account.totalPoints) }}</el-descriptions-item>
                <el-descriptions-item label="累计消耗">{{ pointsText(points.account.usedPoints) }}</el-descriptions-item>
                <el-descriptions-item label="冻结中">{{ pointsText(points.account.frozenPoints) }}</el-descriptions-item>
                <el-descriptions-item label="即将过期">
                  <span :class="{ 'points-warning': Number(points.account.expirePoints) > 0 }">
                    {{ pointsText(points.account.expirePoints) }}
                  </span>
                </el-descriptions-item>
                <el-descriptions-item label="建账时间">
                  {{ validate.formatTime(points.account.createTime) || '-' }}
                </el-descriptions-item>
              </el-descriptions>
              <!-- 账户行是由积分动作建出来的，一条都没获得过就没有这一行，不是数据丢了 -->
              <el-empty v-else description="还没有积分账户（签到、下单、评价都会记分，那时才建这一行账）" :image-size="72" />
            </div>

            <div class="pane-toolbar">
              <el-select v-model="points.query.bizType" placeholder="全部类型" clearable class="pane-filter"
                @change="handlePointsQuery">
                <el-option v-for="item in dict.options(BIZ_TYPE_DICT)" :key="item.value" :label="item.label"
                  :value="item.value" />
              </el-select>
            </div>
            <el-table v-loading="points.loading" :data="points.rows" size="small" border show-overflow-tooltip
              empty-text="还没有积分流水">
              <el-table-column label="时间" align="center" width="150">
                <template #default="{ row }">{{ validate.formatTime(row.createTime) || '-' }}</template>
              </el-table-column>
              <el-table-column label="业务类型" align="center" width="100">
                <template #default="{ row }">
                  <el-tag size="small" :type="dict.tag(BIZ_TYPE_DICT, row.bizType)">
                    {{ dict.label(BIZ_TYPE_DICT, row.bizType) }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="变动" align="right" width="90">
                <template #default="{ row }">
                  <span :class="Number(row.points) < 0 ? 'points-out' : 'points-in'">{{ signedText(row.points) }}</span>
                </template>
              </el-table-column>
              <el-table-column label="变动后余额" align="right" width="100">
                <template #default="{ row }">{{ pointsText(row.balance) }}</template>
              </el-table-column>
              <el-table-column label="关联单号" align="center" min-width="170">
                <template #default="{ row }">{{ row.bizNo || '-' }}</template>
              </el-table-column>
              <el-table-column label="说明" align="center" min-width="160">
                <template #default="{ row }">{{ row.description || '-' }}</template>
              </el-table-column>
              <el-table-column label="过期时间" align="center" width="150">
                <template #default="{ row }">{{ validate.formatTime(row.expireTime) || '-' }}</template>
              </el-table-column>
            </el-table>
            <div class="pagination-container">
              <el-pagination background size="small" v-model:current-page="points.query.pageNum"
                v-model:page-size="points.query.pageSize" :page-sizes="[10, 20, 50]" :total="points.total"
                layout="total, sizes, prev, pager, next, jumper" @size-change="handlePointsSizeChange"
                @current-change="getPointsList" />
            </div>
          </div>
        </el-tab-pane>

        <!-- 钱包：上面一块是账户那几个数（库里存的，不做现算），下面一张表是他这本账的进出明细。
             这一页只有充值、提现两颗写入口，而且走的都是「改登录者自己这本账」那两条共用接口 ——
             账面那几个数改不了：余额只能由充值进、提现出、下单占、退款还这些动作自己加减（同商品库存那一页只改预警参数） -->
        <el-tab-pane label="钱包" name="wallet">
          <div class="pane">
            <!-- 钱进钱出的两颗挂在这本账上面（改的就是这一块，位置同地址那一格的「新增地址」） -->
            <div class="pane-toolbar">
              <el-button type="primary" plain @click="handleWalletChange('recharge')">充值</el-button>
              <el-button plain @click="handleWalletChange('withdraw')">提现</el-button>
              <span class="pane-tip">这两颗动的都是登录账号自己这本账，平台账号坐这一页也一样</span>
            </div>
            <div v-loading="wallet.loadingAccount" class="wallet-account">
              <el-descriptions v-if="wallet.account" :column="3" border>
                <el-descriptions-item label="可用余额">
                  <span class="balance">{{ validate.formatAmount(wallet.account.balance) }}</span>
                </el-descriptions-item>
                <el-descriptions-item label="冻结中">
                  <span :class="{ 'amount-warning': Number(wallet.account.frozenBalance) > 0 }">
                    {{ validate.formatAmount(wallet.account.frozenBalance) }}
                  </span>
                </el-descriptions-item>
                <el-descriptions-item label="赠送余额">
                  {{ validate.formatAmount(wallet.account.giftBalance) }}
                </el-descriptions-item>
                <el-descriptions-item label="累计充值">{{ validate.formatAmount(wallet.account.totalRecharge) }}</el-descriptions-item>
                <el-descriptions-item label="累计消费">{{ validate.formatAmount(wallet.account.totalConsume) }}</el-descriptions-item>
                <el-descriptions-item label="累计提现">{{ validate.formatAmount(wallet.account.totalWithdraw) }}</el-descriptions-item>
                <el-descriptions-item label="账户状态">
                  <el-tag v-if="wallet.account.status != null" size="small"
                    :type="dict.tag(WALLET_STATUS_DICT, wallet.account.status)">
                    {{ dict.label(WALLET_STATUS_DICT, wallet.account.status) }}
                  </el-tag>
                  <span v-else>-</span>
                </el-descriptions-item>
                <el-descriptions-item label="建账时间">
                  {{ validate.formatTime(wallet.account.createTime) || '-' }}
                </el-descriptions-item>
              </el-descriptions>
              <!-- 账户行是由钱包动作建出来的，一次都没动过钱就没有这一行，不是数据丢了 -->
              <el-empty v-else description="还没有钱包账户（点上面那颗「充值」充第一笔就会建出这一行账，下单用余额支付、退款退回余额时也会）" :image-size="72" />
            </div>

            <div class="pane-toolbar">
              <el-select v-model="wallet.query.bizType" placeholder="全部类型" clearable class="pane-filter"
                @change="handleWalletQuery">
                <el-option v-for="item in dict.options(WALLET_BIZ_TYPE_DICT)" :key="item.value" :label="item.label"
                  :value="item.value" />
              </el-select>
            </div>
            <el-table v-loading="wallet.loading" :data="wallet.rows" size="small" border show-overflow-tooltip
              empty-text="还没有钱包流水">
              <el-table-column label="时间" align="center" width="150">
                <template #default="{ row }">{{ validate.formatTime(row.createTime) || '-' }}</template>
              </el-table-column>
              <el-table-column label="业务类型" align="center" width="100">
                <template #default="{ row }">
                  <el-tag size="small" :type="dict.tag(WALLET_BIZ_TYPE_DICT, row.bizType)">
                    {{ dict.label(WALLET_BIZ_TYPE_DICT, row.bizType) }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="变动" align="right" width="110">
                <template #default="{ row }">
                  <span :class="Number(row.amount) < 0 ? 'amount-out' : 'amount-in'">{{ signedAmount(row.amount) }}</span>
                </template>
              </el-table-column>
              <el-table-column label="变动后余额" align="right" width="110">
                <template #default="{ row }">{{ validate.formatAmount(row.balance) }}</template>
              </el-table-column>
              <el-table-column label="关联单号" align="center" min-width="170">
                <template #default="{ row }">{{ row.bizNo || '-' }}</template>
              </el-table-column>
              <el-table-column label="说明" align="center" min-width="160">
                <template #default="{ row }">{{ row.description || '-' }}</template>
              </el-table-column>
            </el-table>
            <div class="pagination-container">
              <el-pagination background size="small" v-model:current-page="wallet.query.pageNum"
                v-model:page-size="wallet.query.pageSize" :page-sizes="[10, 20, 50]" :total="wallet.total"
                layout="total, sizes, prev, pager, next, jumper" @size-change="handleWalletSizeChange"
                @current-change="getWalletList" />
            </div>
          </div>
        </el-tab-pane>

        <!-- 地址：名下那几条收货地址，能新增、能改、能删（设默认就在「修改」里勾）。这一页不显「消费者」那一格 ——
             动的人就是本人，新增只往自己这本地址簿里加（服务端把 userId 钉成登录 id，前端传什么都不认） -->
        <el-tab-pane label="地址" name="address">
          <div class="pane">
            <div class="pane-toolbar">
              <el-button v-permission="['shop:address:add']" type="primary" plain icon="Plus"
                @click="handleAddressAdd">新增地址</el-button>
              <span class="pane-tip">下单时选的就是这几条；第一条会自动设成默认，换默认地址点「修改」勾上默认</span>
            </div>
            <el-table v-loading="address.loading" :data="address.rows" size="small" border show-overflow-tooltip
              empty-text="还没有收货地址">
              <el-table-column label="收货人" prop="receiverName" align="center" min-width="100" />
              <el-table-column label="收货电话" prop="receiverPhone" align="center" min-width="120" />
              <!-- <el-table-column label="所在地区" align="center" min-width="160">
                <template #default="{ row }">{{ areaOf(row) }}</template>
              </el-table-column> -->
              <el-table-column label="详细地址" prop="detail" align="center" min-width="180" />
              <el-table-column label="标签" align="center" width="90">
                <template #default="{ row }">
                  <el-tag v-if="row.tag" size="small" :type="dict.tag(TAG_DICT, row.tag)">
                    {{ dict.label(TAG_DICT, row.tag) }}
                  </el-tag>
                  <span v-else>-</span>
                </template>
              </el-table-column>
              <el-table-column label="默认" align="center" width="80">
                <template #default="{ row }">
                  <el-tag v-if="row.isDefault === 1" size="small" type="success">默认</el-tag>
                  <span v-else>-</span>
                </template>
              </el-table-column>
              <el-table-column label="创建时间" align="center" width="150">
                <template #default="{ row }">{{ validate.formatTime(row.createTime, 'YYYY-MM-DD') || '-' }}</template>
              </el-table-column>
              <el-table-column label="操作" align="center" width="150" fixed="right">
                <template #default="{ row }">
                  <el-button v-permission="['shop:address:update']" type="primary" link icon="Edit"
                    @click="handleAddressUpdate(row)">修改</el-button>
                  <el-button v-permission="['shop:address:delete']" type="danger" link icon="Delete"
                    @click="handleAddressDelete(row)">删除</el-button>
                </template>
              </el-table-column>
            </el-table>
            <div class="pagination-container">
              <el-pagination background size="small" v-model:current-page="address.query.pageNum"
                v-model:page-size="address.query.pageSize" :page-sizes="[10, 20, 50]" :total="address.total"
                layout="total, sizes, prev, pager, next, jumper" @size-change="handleAddressSizeChange"
                @current-change="getAddressList" />
            </div>
          </div>
        </el-tab-pane>

        <!--
          广场消息：这一位名下发过的那几条都在这一串里（待审核和被拒的也在 —— 走的是「广场消息」那一页
          同一条列表接口，只多递一格 userId 把范围收到他名下，审核状态不筛就全给）。
          「修改」只动 置顶 / 推荐 / 上下架 三格：正文和关联对象不由后台改（那是作者说的话，
          改完了他在广场上看到的就不是自己写的那一版），要处理内容就回「广场消息」那一页拒绝或下架。
          没有这两颗权限的账号（消费者自己坐这一页）只看得见列表，操作列整列不渲染。
        -->
        <el-tab-pane label="广场消息" name="plaza">
          <div class="pane">
            <div class="pane-toolbar">
              <el-select v-model="plaza.query.auditStatus" placeholder="全部审核状态" clearable class="pane-filter"
                @change="handlePlazaQuery">
                <el-option v-for="item in dict.options(PLAZA_AUDIT_DICT)" :key="item.value" :label="item.label"
                  :value="item.value" />
              </el-select>
              <span class="pane-tip">正文由作者自己在广场那一页改，改完会重新回到审核队列</span>
            </div>
            <el-table v-loading="plaza.loading" :data="plaza.rows" size="small" border show-overflow-tooltip
              empty-text="还没在广场上发过消息">
              <el-table-column label="标题" align="left" min-width="140">
                <template #default="{ row }">{{ row.title || '（没填标题）' }}</template>
              </el-table-column>
              <!-- 正文是访客写的富文本，这一列只铺服务端剥好标签的那份摘要，不能 v-html -->
              <el-table-column label="正文摘要" align="left" min-width="220" prop="contentText" />
              <el-table-column label="关联对象" align="center" min-width="120">
                <template #default="{ row }">
                  <el-tag v-if="row.relationType" size="small">
                    {{ dict.label(PLAZA_RELATION_DICT, row.relationType) }}
                  </el-tag>
                  <span>{{ plazaRelationText(row) }}</span>
                </template>
              </el-table-column>
              <el-table-column label="点赞" align="center" width="70">
                <template #default="{ row }">{{ row.likeCount || 0 }}</template>
              </el-table-column>
              <el-table-column label="评论" align="center" width="70">
                <template #default="{ row }">{{ row.commentCount || 0 }}</template>
              </el-table-column>
              <el-table-column label="分享" align="center" width="70">
                <template #default="{ row }">{{ row.shareCount || 0 }}</template>
              </el-table-column>
              <el-table-column label="标记" align="center" width="90">
                <template #default="{ row }">
                  <el-tag v-if="row.isTop === 1" size="small" type="danger">置顶</el-tag>
                  <el-tag v-if="row.isRecommend === 1" size="small" type="warning">推荐</el-tag>
                  <span v-if="row.isTop !== 1 && row.isRecommend !== 1">-</span>
                </template>
              </el-table-column>
              <el-table-column label="审核状态" align="center" width="90">
                <template #default="{ row }">
                  <el-tag size="small" :type="dict.tag(PLAZA_AUDIT_DICT, row.auditStatus)">
                    {{ dict.label(PLAZA_AUDIT_DICT, row.auditStatus) }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="审核说明" align="center" min-width="140">
                <template #default="{ row }">{{ row.auditRemark || '-' }}</template>
              </el-table-column>
              <el-table-column label="状态" align="center" width="80">
                <template #default="{ row }">
                  <el-tag size="small" :type="dict.tag(PLAZA_STATUS_DICT, row.status)">
                    {{ dict.label(PLAZA_STATUS_DICT, row.status) }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="时间" align="center" width="150">
                <template #default="{ row }">
                  {{ validate.formatTime(row.publishTime || row.createTime) || '-' }}
                </template>
              </el-table-column>
              <el-table-column label="操作" align="center" width="150" fixed="right">
                <template #default="{ row }">
                  <el-button v-permission="['sys:plaza:update']" type="primary" link icon="Edit"
                    @click="handlePlazaUpdate(row)">修改</el-button>
                  <el-button v-permission="['sys:plaza:delete']" type="danger" link icon="Delete"
                    @click="handlePlazaDelete(row)">删除</el-button>
                </template>
              </el-table-column>
            </el-table>
            <div class="pagination-container">
              <el-pagination background size="small" v-model:current-page="plaza.query.pageNum"
                v-model:page-size="plaza.query.pageSize" :page-sizes="[10, 20, 50]" :total="plaza.total"
                layout="total, sizes, prev, pager, next, jumper" @size-change="handlePlazaSizeChange"
                @current-change="getPlazaList" />
            </div>
          </div>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <!--
      广场消息修改弹窗：只有 置顶 / 推荐 / 上下架 三格能动（后端那一趟也只认这三格），
      标题和审核状态摆在上面当回显，免得人在改完之前不知道改的是哪一条。
      形状同「广场消息」那一页的修改弹窗。
    -->
    <el-dialog v-model="plazaOpen" title="修改广场消息" width="520px" append-to-body destroy-on-close>
      <el-form :model="plazaForm" label-width="90px">
        <el-form-item label="标题">
          <span>{{ plazaForm.title || '（没填标题）' }}</span>
        </el-form-item>
        <el-form-item label="审核状态">
          <el-tag size="small" :type="dict.tag(PLAZA_AUDIT_DICT, plazaForm.auditStatus)">
            {{ dict.label(PLAZA_AUDIT_DICT, plazaForm.auditStatus) }}
          </el-tag>
        </el-form-item>
        <el-form-item label="是否置顶">
          <el-switch v-model="plazaForm.isTop" :active-value="1" :inactive-value="0" />
        </el-form-item>
        <el-form-item label="是否推荐">
          <el-switch v-model="plazaForm.isRecommend" :active-value="1" :inactive-value="0" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="plazaForm.status" placeholder="请选择" style="width: 100%">
            <el-option v-for="item in plazaStatusOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="plazaOpen = false">取 消</el-button>
          <el-button type="primary" :loading="plazaSubmitLoading || plazaDetailLoading"
            @click="submitPlaza">确 定</el-button>
        </div>
      </template>
    </el-dialog>

    <!--
      签到记录：日历弹窗，公共组件 SigninCalendar（和「签到管理」那页同一份）。
      这里传 self：看的是登录者自己那几行，两颗写的动作都走 comm（只验登录态、userId 由服务端钉），
      补签只补得到最近 7 天。
    -->
    <SigninCalendar v-model:visible="signinOpen" :user-id="base.data.id" self @changed="refreshPoints" />

    <!--
      基础信息修改弹窗：表单只收服务端认的那几格（头像/昵称/手机号/邮箱/性别/所在地区/详细地址/经纬度/个性签名）。
      账号、账号类型、状态、角色不进表单 —— 走的是 /sys/user/myProfile 那一条免权限接口，
      服务端把 id 钉成登录 id、其余列一律不碰，所以这一颗按钮也不用给账号勾 sys:user:update。
      地区和经纬度的写法照抄下面地址那份（级联的 value 是行政区划代码，提交时 join 成 area_code，中文地名解析成 area_zh）。
    -->
    <el-dialog v-model="baseOpen" title="修改基本信息" width="720px" append-to-body destroy-on-close>
      <el-form ref="baseFormRef" :model="baseForm" :rules="baseRules" label-width="110px">
        <el-row :gutter="20">
          <el-col :span="24">
            <el-form-item label="头像" prop="avatar">
              <upload-image v-model="baseForm.avatar" :size="120" source="avatar" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="昵称" prop="nickname">
              <el-input v-model="baseForm.nickname" placeholder="请输入昵称" maxlength="30" show-word-limit />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="手机号" prop="mobile">
              <el-input v-model="baseForm.mobile" placeholder="请输入手机号" maxlength="20" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="邮箱" prop="email">
              <el-input v-model="baseForm.email" placeholder="请输入邮箱" maxlength="50" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="性别" prop="sex">
              <el-radio-group v-model="baseForm.sex">
                <el-radio :value="1">男</el-radio>
                <el-radio :value="2">女</el-radio>
                <el-radio :value="0">保密</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="所在地区">
              <el-cascader v-model="baseAreaArr" :options="colPickerData"
                :props="{ label: 'text', value: 'value', children: 'children' }" style="width: 100%" clearable
                placeholder="请选择所在地区" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="详细地址" prop="address">
              <el-input v-model="baseForm.address" placeholder="街道、门牌号" maxlength="255" />
            </el-form-item>
          </el-col>
          <el-col :span="18">
            <el-form-item label="经纬度">
              <div class="pair-field">
                <el-input-number v-model="baseForm.longitude" :precision="6" :controls="false"
                  placeholder="经度 如 120.150000" style="width: 100%" />
                <span class="pair-split">-</span>
                <el-input-number v-model="baseForm.latitude" :precision="6" :controls="false"
                  placeholder="纬度 如 30.250000" style="width: 100%" />
              </div>
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <!-- 整表 label-width 是 110px，这一格只有 6 格宽，不取消 label 按钮就顶出去了 -->
            <el-form-item label-width="0">
              <shop-map :longitude="baseForm.longitude" :latitude="baseForm.latitude" @confirm="handleBaseLocationConfirm" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="个性签名" prop="signature">
              <el-input v-model="baseForm.signature" type="textarea" :rows="2" maxlength="255" show-word-limit
                placeholder="选填" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="baseOpen = false">取 消</el-button>
          <el-button type="primary" :loading="baseSubmitLoading" @click="submitBase">确 定</el-button>
        </div>
      </template>
    </el-dialog>

    <!--
      收货地址新增/修改：表单照抄「收货地址」那页的那份，只少了「消费者」那一格 ——
      这一页动的就是登录者自己这本地址簿（改的时候 userId 沿用那一行原来的归属，不挪给别人）。
      地区/经纬度的写法也从那页搬过来：级联的 value 是行政区划代码，
      提交时 join 成 area_code，中文地名顺手解析成 area_zh。
    -->
    <el-dialog v-model="addressOpen" :title="addressTitle" width="720px" append-to-body destroy-on-close>
      <el-form ref="addressFormRef" :model="addressForm" :rules="addressRules" label-width="110px">
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="收货人姓名" prop="receiverName">
              <el-input v-model="addressForm.receiverName" placeholder="请输入收货人姓名" maxlength="50" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="收货人电话" prop="receiverPhone">
              <el-input v-model="addressForm.receiverPhone" placeholder="请输入收货人电话" maxlength="20" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="标签" prop="tag">
              <!-- 字典里有的直接选，没有的允许现场输一个：这一列本来就是自由文本 -->
              <el-select v-model="addressForm.tag" placeholder="选填，如 家/公司/学校" style="width: 100%" clearable
                filterable allow-create default-first-option>
                <el-option v-for="item in dict.options(TAG_DICT)" :key="item.value" :label="item.label"
                  :value="item.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="是否默认" prop="isDefault">
              <el-radio-group v-model="addressForm.isDefault">
                <el-radio :value="1">默认地址</el-radio>
                <el-radio :value="0">否</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="所在地区">
              <el-cascader v-model="areaArr" :options="colPickerData"
                :props="{ label: 'text', value: 'value', children: 'children' }" style="width: 100%" clearable
                placeholder="请选择所在地区" />
            </el-form-item>
          </el-col>
          <el-col :span="18">
            <el-form-item label="经纬度">
              <div class="pair-field">
                <el-input-number v-model="addressForm.longitude" :precision="6" :controls="false"
                  placeholder="经度 如 120.150000" style="width: 100%" />
                <span class="pair-split">-</span>
                <el-input-number v-model="addressForm.latitude" :precision="6" :controls="false"
                  placeholder="纬度 如 30.250000" style="width: 100%" />
              </div>
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <!-- 整表 label-width 是 110px，这一格只有 6 格宽，不取消 label 按钮就顶出去了 -->
            <el-form-item label-width="0">
              <shop-map :longitude="addressForm.longitude" :latitude="addressForm.latitude"
                @confirm="handleLocationConfirm" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="详细地址" prop="detail">
              <el-input v-model="addressForm.detail" placeholder="街道、门牌号" maxlength="255" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="邮编" prop="postalCode">
              <el-input v-model="addressForm.postalCode" placeholder="选填" maxlength="10" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="addressOpen = false">取 消</el-button>
          <el-button type="primary" :loading="addressSubmitLoading" @click="submitAddress">确 定</el-button>
        </div>
      </template>
    </el-dialog>

    <!--
      充值 / 提现：一份表单两件事，只有金额和说明两格 —— 账面那几个数填不了（这一本是账，余额只能由
      充值进、提现出、下单占、退款还这些动作自己加减），也没有「给谁充」那一格：服务端第一句把 userId
      钉成登录 id，动的就是登录者自己这本账（免权限 + 钉 id 这两条同上面那份基础信息弹窗）。
      提现的上限摆在可用余额那一格上，但真闸门是服务层那条 status = 1 AND balance >= 这一笔。
    -->
    <el-dialog v-model="walletOpen" :title="walletAction === 'recharge' ? '余额充值' : '余额提现'" width="520px"
      append-to-body destroy-on-close>
      <el-form ref="walletFormRef" :model="walletForm" :rules="walletRules" label-width="90px">
        <el-form-item label="当前可用">
          <el-input :model-value="validate.formatAmount(walletUsable)" disabled />
        </el-form-item>
        <el-form-item label="金额" prop="amount">
          <!-- 只给正数、两位小数（库里那一列是 decimal(14,2)，多于两位服务端是拒的，不是四舍五入） -->
          <el-input-number v-model="walletForm.amount" :min="0.01" :precision="2" :step="10" controls-position="right"
            placeholder="单位元" style="width: 100%" />
        </el-form-item>
        <el-form-item label="说明" prop="description">
          <el-input v-model="walletForm.description" type="textarea" :rows="2" maxlength="255" show-word-limit
            placeholder="选填，这一格会原样写进钱包流水，日后对账就看它" />
        </el-form-item>
      </el-form>
      <p class="dialog-tip">
        {{ walletAction === 'recharge'
          ? '充进来的钱加到可用余额，同时记一行「充值」流水；第一次充值时那一行钱包账户也是这一刻建出来的。本期不走真收款的渠道（微信那条充值单的链还没接），动的只是账上的数。'
          : '提现是从可用余额里减掉这一笔，同时记一行「提现」流水。下单没付的单子占着的那一部分在「冻结中」，那一部分提不走；账户被冻住时也提不走。本期这一步同样不出真钱，钱是真给出去那一步走线下。' }}
      </p>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="walletOpen = false">取 消</el-button>
          <el-button type="primary" :loading="walletSubmitLoading" @click="submitWallet">确 定</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import BadgeView from '@/components/BadgeView/index.vue'
import SigninCalendar from '@/components/SigninCalendar/index.vue'
import ShopMap from '@/components/ShopMap/index.vue'
import UploadImage from '@/components/Upload/Image.vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { listShopOrderApi } from '@/api/consumer/orderItem'
import {
  listShopAddressApi,
  detailShopAddressApi,
  addShopAddressApi,
  updateShopAddressApi,
  deleteShopAddressApi
} from '@/api/consumer/address'
import { listShopCouponRecordApi } from '@/api/activity/coupon'
import { listShopBadgeRecordApi, updateShopBadgeRecordShowApi } from '@/api/activity/badge'
import { listShopPointsApi, listShopPointsRecordApi } from '@/api/activity/points'
import { listShopWalletApi, listShopWalletRecordApi, rechargeShopWalletApi, withdrawShopWalletApi } from '@/api/consumer/wallet'
import { listShopPlazaApi, detailShopPlazaApi, updateShopPlazaApi, deleteShopPlazaApi } from '@/api/site/plaza'
import { getUserProfileApi, updateMyProfileApi } from '@/api/system/user'
import { useDict } from '@/utils/dictCache'
import validate from '@/utils/validate'
import { useColPickerData } from '@/composables/useColPickerData'
import { useUserStore } from '@/store/modules/user'

const ORDER_DICT = 'order_status'
const COUPON_TYPE_DICT = 'coupon_type'
/** 用户券状态（0未使用 1已使用 2已过期 3已作废）：这一页看的是手里那一张张券，不是模板 */
const COUPON_STATUS_DICT = 'coupon_user_status'
const GET_WAY_DICT = 'get_way'
const BIZ_TYPE_DICT = 'points_biz_type'
const TAG_DICT = 'address_tag'
/** 钱包那两块：流水的业务类型（八档）和账户状态（两档），都是枚举列，标签走字典 */
const WALLET_BIZ_TYPE_DICT = 'wallet_biz_type'
const WALLET_STATUS_DICT = 'wallet_status'
/** 徽章那一格三档：徽章类型（定义表那一格，联表带出）、获得来源、用户徽章状态，都是字典列 */
const BADGE_TYPE_DICT = 'badge_type'
const BADGE_SOURCE_DICT = 'badge_source'
const BADGE_STATUS_DICT = 'user_badge_status'
/** 广场消息那一档三列都是枚举：关联类型、审核状态、上下架（同「广场消息」那一页用的同一份字典） */
const PLAZA_RELATION_DICT = 'plaza_relation_type'
const PLAZA_AUDIT_DICT = 'plaza_audit_status'
const PLAZA_STATUS_DICT = 'plaza_status'

const dict = useDict('account_type', ORDER_DICT, COUPON_TYPE_DICT, COUPON_STATUS_DICT, GET_WAY_DICT, BIZ_TYPE_DICT,
  TAG_DICT, WALLET_BIZ_TYPE_DICT, WALLET_STATUS_DICT, BADGE_TYPE_DICT, BADGE_SOURCE_DICT, BADGE_STATUS_DICT,
  PLAZA_RELATION_DICT, PLAZA_AUDIT_DICT, PLAZA_STATUS_DICT)

/** 地区：area_zh 是冗余存下来的中文，老数据没存就按 area_code 现解析（同收货地址那页） */
const { colPickerData, resolveAreaCode } = useColPickerData()
const areaOf = (row: any) => row.areaZh || resolveAreaCode(row.areaCode) || '-'

const userStore = useUserStore()
/** 「是否展示」那一格是开关、不是按钮，挂不了 v-permission（那颗指令是不渲染），只能在这儿自己判 —— 形状同「徽章管理」那页 */
const permissions = computed(() => userStore.user.permissions || [])
const hasPermission = (permission: string): boolean => {
  return permissions.value.includes(permission)
}

/**
 * 八个 tab 一份一块，形状一样：rows / total / query / loading。
 * 账户那一格（points.account、wallet.account）是个例外 —— 它是唯一的一行，不是分页列表。
 */
const base = reactive<any>({ loading: false, data: {} })
const order = reactive<any>({ loading: false, rows: [], total: 0, query: { pageNum: 1, pageSize: 10, status: undefined } })
const coupon = reactive<any>({ loading: false, rows: [], total: 0, query: { pageNum: 1, pageSize: 10, status: undefined } })
const badge = reactive<any>({
  loading: false,
  rows: [],
  total: 0,
  query: { pageNum: 1, pageSize: 10, source: undefined, status: undefined },
  /** 「是否展示」那颗开关正在等回话的行 id：按住这一格，免得连点两下把服务端拨乱 */
  showLoadingId: null as any
})
const points = reactive<any>({
  loading: false,
  loadingAccount: false,
  rows: [],
  total: 0,
  account: null,
  query: { pageNum: 1, pageSize: 10, bizType: undefined }
})
const wallet = reactive<any>({
  loading: false,
  loadingAccount: false,
  rows: [],
  total: 0,
  account: null,
  query: { pageNum: 1, pageSize: 10, bizType: undefined }
})
const address = reactive<any>({ loading: false, rows: [], total: 0, query: { pageNum: 1, pageSize: 10 } })
/** 广场消息：读的是「广场消息」那一页同一条列表接口，多递一格 userId 把范围收到这一位名下 */
const plaza = reactive<any>({ loading: false, rows: [], total: 0, query: { pageNum: 1, pageSize: 10, auditStatus: undefined } })

/**
 * 广场消息修改弹窗：后台只动得了 置顶 / 推荐 / 上下架 三格（服务层那一趟只认这三格，
 * 正文和关联对象一概不收 —— 那是作者说的话），所以表单里不摆标题和正文的输入框。
 * 三条都不带必填：开关不吃空值，状态那一格由列表那一行垫底，进来就有数。
 */
const plazaOpen = ref(false)
const plazaSubmitLoading = ref(false)
/** 详情那一趟没回来之前先别让人点确定：这时候 plazaForm.content 还没有，提交等于把正文清空 */
const plazaDetailLoading = ref(false)
const plazaForm = reactive<any>({})
/** 0 是历史值（删除走 is_deleted），下拉里不给选 —— 同「广场消息」那一页 */
const plazaStatusOptions = computed(() => dict.options(PLAZA_STATUS_DICT).filter((item: any) => item.value !== 0))

/** 关联对象那一格：名字是联表现取的，对象删掉了就按 id 说 */
const plazaRelationText = (row: any) => {
  if (!row?.relationType || !row?.relationId) return '无关联'
  return row.relationName || `已删除对象 #${row.relationId}`
}

/**
 * 收货地址的新增/修改弹窗（一份表单两件事，同收货地址那页）。地址归属（userId）不在表单里收 ——
 * 新增时送登录 id（服务端对消费者账号也会覆盖成登录 id；平台账号坐这一页时不覆盖，不送就撞「请选择消费者」），
 * 修改时沿用这一行原来的归属，所以改不动别人名下的地址（服务层 assertOwn 那一道在同一条链上）。
 */
const addressOpen = ref(false)
const addressTitle = ref('')
const addressSubmitLoading = ref(false)
const addressFormRef = ref<FormInstance>()
const addressForm = reactive<any>({})
const areaArr = ref<string[]>([])

const addressRules = reactive<FormRules>({
  receiverName: [{ required: true, message: '收货人姓名不能为空', trigger: 'blur' }],
  receiverPhone: [{ required: true, message: '收货人电话不能为空', trigger: 'blur' }],
  detail: [{ required: true, message: '详细地址不能为空', trigger: 'blur' }]
})

/**
 * 余额充值 / 提现弹窗（一份表单两件事，靠 walletAction 分档，写法同上面那份地址弹窗）。
 * 表单只有金额和说明：账上那几个数一概不在里面 —— 这一本是账，余额只能由充值进、提现出、
 * 下单占、退款还这些动作自己加加减减，页面能填余额就等于允许「账上多了 500 块、流水里什么都没有」
 *（同商品库存那一页只改预警参数、不改库存数）。
 * 也没有「给谁充」那一格：这一颗改的是登录者自己这本账，服务端第一句把 userId 钉成登录 id
 *（和上面那份基础信息弹窗同一个道理 —— 免权限，但也就动不了别人的账）。
 */
const walletOpen = ref(false)
const walletAction = ref<'recharge' | 'withdraw'>('recharge')
const walletSubmitLoading = ref(false)
const walletFormRef = ref<FormInstance>()
const walletForm = reactive<any>({ amount: undefined, description: undefined })

/** 能提的上限就是账上那一格可用余额（还没开账时按 0 元说）：真闸门在服务层那条 balance >= 这一笔 上，这里只是先把话说在前面 */
const walletUsable = computed(() => Number(wallet.account?.balance ?? 0))

/** 金额最多两位小数（库里那一列是 decimal(14,2)，服务端这一条是拒的、不是四舍五入），所以在这儿一起拦 */
const walletRules = reactive<FormRules>({
  amount: [
    { required: true, message: '请填写金额', trigger: 'blur' },
    {
      validator: (_rule: any, value: any, callback: any) => {
        if (value === '' || value == null) return callback()
        const amount = Number(value)
        if (Number.isNaN(amount)) return callback(new Error('金额请填个数'))
        if (amount <= 0) return callback(new Error('金额要大于 0'))
        if (Math.round(amount * 100) / 100 !== amount) return callback(new Error('金额最多两位小数'))
        if (walletAction.value === 'withdraw' && amount > walletUsable.value)
          return callback(new Error(`能提的余额只有 ${validate.formatAmount(walletUsable.value)}，冻结中那一部分提不走`))
        callback()
      },
      trigger: 'blur'
    }
  ]
})

/** 开弹窗：两颗共用一份表单，所以每次进来都把上一笔的数清掉（弹窗是 destroy-on-close，校验状态也跟着重建） */
const handleWalletChange = (action: 'recharge' | 'withdraw') => {
  walletAction.value = action
  walletForm.amount = undefined
  walletForm.description = undefined
  walletOpen.value = true
}

/**
 * 提交充值 / 提现：账和那行流水在同一个事务里写完，服务端回的是改完之后的那一行账户。
 * 这里不拿它回填，仍旧按读接口重拉账户行 + 流水 —— 两处形状一致（列表那条联了昵称和手机号），
 * 而且流水新记的那一笔要出现在表上，「共 N 笔」也得跟着变。
 */
const submitWallet = () => {
  walletFormRef.value?.validate(async (valid: any) => {
    if (!valid) return
    walletSubmitLoading.value = true
    try {
      const body = { amount: walletForm.amount, description: walletForm.description }
      await (walletAction.value === 'recharge' ? rechargeShopWalletApi(body) : withdrawShopWalletApi(body))
      ElMessage.success(walletAction.value === 'recharge' ? '充值已到账' : '提现已记下')
      walletOpen.value = false
      await getWalletAccount()
      wallet.query.pageNum = 1
      await getWalletList()
    } catch (error) {
    } finally {
      walletSubmitLoading.value = false
    }
  })
}

/**
 * 签到记录弹窗（公共组件 SigninCalendar，self 模式）：它不在五个 tab 里占一格，
 * 而是卡片顶上那一颗按钮（作用的不是某一块表格，而是"这个人这个月签了几天"）；
 * 捞记录、换月、签到/补签都在组件里。
 */
const signinOpen = ref(false)

/**
 * 基础信息的修改弹窗。送出的是服务端白名单里的那十一格，id 不在里面 ——
 * /sys/user/myProfile 那条接口把 id 钉成登录 id，所以这一颗按钮不用给账号勾 sys:user:update，
 * 也改不动别人的档案（同理账号/账号类型/状态/角色这几格不放进表单，服务端也不认）。
 * 这张表全局是 field-strategy=not_empty，空值不写库：这几格改得了、清不掉。
 */
const baseOpen = ref(false)
const baseSubmitLoading = ref(false)
const baseFormRef = ref<FormInstance>()
const baseForm = reactive<any>({})
const baseAreaArr = ref<string[]>([])

/** 手机号那一格和后端那条一样按 1[3-9] 开头判，查重不在这一页做（用户管理那页也没查） */
const baseRules = reactive<FormRules>({
  nickname: [{ required: true, message: '昵称不能为空', trigger: 'blur' }],
  mobile: [{ pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确', trigger: 'blur' }],
  email: [{ type: 'email', message: '邮箱格式不正确', trigger: 'blur' }]
})

/**
 * 点哪个 tab 才去拉哪一块（el-tab-pane 默认不是懒渲染的，八个 pane 一进来就全在场，
 * 所以闸门挂在这里而不是 tab 上）：订单、券、徽章、积分、钱包、地址各一到两条 SQL，没必要为一次进页面全跑一遍。
 */
const activeTab = ref('base')
const loaded: Record<string, boolean> = {}

/** 基础信息 */
const getBase = async () => {
  base.loading = true
  try {
    const { data } = await getUserProfileApi()
    // 接口回的是 { sysUser, roles }：角色挂在最外层，那一行用户信息里没有
    base.data = { ...(data?.sysUser || {}), roles: data?.roles || [] }
  } catch (error) {
  } finally {
    base.loading = false
  }
}

/** 订单 */
const getOrderList = async () => {
  order.loading = true
  try {
    const { data } = await listShopOrderApi(order.query)
    order.rows = data.records || []
    order.total = data.total || 0
  } catch (error) {
  } finally {
    order.loading = false
  }
}

/** 优惠券：走发放记录那一条，服务端把 userId 钉成登录 id，couponId 不带就是看他名下全部券 */
const getCouponList = async () => {
  coupon.loading = true
  try {
    const { data } = await listShopCouponRecordApi(coupon.query)
    coupon.rows = data.records || []
    coupon.total = data.total || 0
  } catch (error) {
  } finally {
    coupon.loading = false
  }
}

/** 徽章：走「徽章管理 → 获取记录」那一条，那边只对消费者账号钉登录 id，平台账号坐这一页要自己把 id 递过去（同钱包那两条） */
const getBadgeList = async () => {
  badge.loading = true
  try {
    const { data } = await listShopBadgeRecordApi({ ...badge.query, userId: base.data?.id })
    badge.rows = data.records || []
    badge.total = data.total || 0
  } catch (error) {
  } finally {
    badge.loading = false
  }
}

/** 改这一枚要不要给人看：走的就是「徽章管理 → 获取记录」里那一格同一个端点、同一套判据（只动 is_show 一列，
 *  不收积分不收券、也不删这一行；服务端按账号类型分档 —— 平台/门店/ERP 能改任意一行，消费者账号只认自己名下） */
const handleBadgeShowChange = async (row: any) => {
  badge.showLoadingId = row.id
  try {
    await updateShopBadgeRecordShowApi(row.id, row.isShow)
    ElMessage.success('修改成功')
  } catch (error) {
    row.isShow = row.isShow === 1 ? 0 : 1
  } finally {
    badge.showLoadingId = null
  }
}

/** 积分账户：账户按用户唯一，所以取第一行就是自己那一行；没获得过积分就没有这一行 */
const getPointsAccount = async () => {
  points.loadingAccount = true
  try {
    const { data } = await listShopPointsApi({ pageNum: 1, pageSize: 1 })
    points.account = (data.records || [])[0] || null
  } catch (error) {
  } finally {
    points.loadingAccount = false
  }
}

/** 积分流水 */
const getPointsList = async () => {
  points.loading = true
  try {
    const { data } = await listShopPointsRecordApi(points.query)
    points.rows = data.records || []
    points.total = data.total || 0
  } catch (error) {
  } finally {
    points.loading = false
  }
}

/**
 * 钱包账户：账户按用户唯一，所以取第一行。
 * 但这一条读接口是「钱包管理」那页的（平台账号坐那一页要看全部），服务端只给消费者账号钉登录 id ——
 * 平台账号坐这一页时必须把 id 递过去，不然拿到的第一行是余额最高的那本别人账。
 * 递的这个 id 不是手填的，是基础信息那一条（/sys/user/myProfile，服务端钉登录 id）带回来的那一行。
 */
const getWalletAccount = async () => {
  wallet.loadingAccount = true
  try {
    const { data } = await listShopWalletApi({ pageNum: 1, pageSize: 1, userId: base.data?.id })
    wallet.account = (data.records || [])[0] || null
  } catch (error) {
  } finally {
    wallet.loadingAccount = false
  }
}

/** 钱包流水：同上，那条读接口给「流水」弹窗用是不收窄的，这一页按登录 id 收窄 */
const getWalletList = async () => {
  wallet.loading = true
  try {
    const { data } = await listShopWalletRecordApi({ ...wallet.query, userId: base.data?.id })
    wallet.rows = data.records || []
    wallet.total = data.total || 0
  } catch (error) {
  } finally {
    wallet.loading = false
  }
}

/** 地址 */
const getAddressList = async () => {
  address.loading = true
  try {
    const { data } = await listShopAddressApi(address.query)
    address.rows = data.records || []
    address.total = data.total || 0
  } catch (error) {
  } finally {
    address.loading = false
  }
}

/** 广场消息 */
const getPlazaList = async () => {
  plaza.loading = true
  try {
    const { data } = await listShopPlazaApi({ ...plaza.query, userId: base.data.id })
    plaza.rows = data.records || []
    plaza.total = data.total || 0
  } catch (error) {
  } finally {
    plaza.loading = false
  }
}

const loaders: Record<string, () => Promise<void>> = {
  base: getBase,
  order: getOrderList,
  coupon: getCouponList,
  // 徽章走的是「徽章管理」那页获取记录那一条，服务端只给消费者账号钉登录 id ——
  // 平台账号坐这一页也必须把 id 递过去，不然看到的是全平台谁拿了哪些徽章（同下面钱包那两条例外）
  badge: async () => {
    await loadTab('base')
    await getBadgeList()
  },
  // 这一块的账户和流水是两个接口：账户那五个数是汇总，流水是进出明细，各查各的
  points: async () => {
    await getPointsAccount()
    await getPointsList()
  },
  // 钱包和积分同形：上面账户那一行、下面流水，也是两个接口。
  // 多一步先把基础信息捞回来 —— 那一条带的 id 就是登录 id，钱包那两条读接口拿它收窄（平台账号那一头服务端不钉）
  wallet: async () => {
    await loadTab('base')
    await getWalletAccount()
    await getWalletList()
  },
  address: getAddressList,
  // 广场这一条要多递登录 id，所以先把基础信息捞回来（同上面徽章、钱包那两档）
  plaza: async () => {
    await loadTab('base')
    await getPlazaList()
  }
}

const loadTab = async (name: string) => {
  if (loaded[name]) return
  loaded[name] = true
  await loaders[name]()
}

const handleTabChange = (name: any) => loadTab(String(name))

/** 换了筛选项就回到第一页：接着上一页的页码筛，容易筛出个空列表还以为是没数据 */
const handleOrderQuery = () => {
  order.query.pageNum = 1
  getOrderList()
}
const handleCouponQuery = () => {
  coupon.query.pageNum = 1
  getCouponList()
}
const handleBadgeQuery = () => {
  badge.query.pageNum = 1
  getBadgeList()
}
const handlePointsQuery = () => {
  points.query.pageNum = 1
  getPointsList()
}
const handleWalletQuery = () => {
  wallet.query.pageNum = 1
  getWalletList()
}

const handleOrderSizeChange = () => {
  order.query.pageNum = 1
  getOrderList()
}
const handleCouponSizeChange = () => {
  coupon.query.pageNum = 1
  getCouponList()
}
const handleBadgeSizeChange = () => {
  badge.query.pageNum = 1
  getBadgeList()
}
const handlePointsSizeChange = () => {
  points.query.pageNum = 1
  getPointsList()
}
const handleWalletSizeChange = () => {
  wallet.query.pageNum = 1
  getWalletList()
}
const handleAddressSizeChange = () => {
  address.query.pageNum = 1
  getAddressList()
}
const handlePlazaQuery = () => {
  plaza.query.pageNum = 1
  getPlazaList()
}
const handlePlazaSizeChange = () => {
  plaza.query.pageNum = 1
  getPlazaList()
}

/**
 * 签到给的分是直接进积分账户的，所以日历里签完要把积分那一块同步刷新：
 * 已经点开过积分那个 tab 才刷（没点开过的下一次自然拉到最新的那五个数）
 */
const refreshPoints = () => {
  if (!loaded.points) return
  getPointsAccount()
  getPointsList()
}

/** 签到记录按钮操作：日历组件要个 userId（非消费者账号服务端不覆盖），取的就是基础信息那一格，没捞过就先捞一次 */
const handleSignin = async () => {
  if (!base.data.id) await getBase()
  signinOpen.value = true
}

/**
 * 打开基础信息弹窗：直接拿已经捞到的那一行回显（这一页读的就是自己，不用再走一次详情）。
 * area_code 是 "110000,110100,110101" 这种，split 回级联选择器要的数组。
 * destroy-on-close 会重建表单实例，校验痕迹还是清一次保险 —— 上次没改完就关掉、这次重开不该带着红字。
 */
const handleBaseUpdate = async () => {
  if (!base.data.id) await getBase()
  Object.assign(baseForm, {
    avatar: base.data.avatar,
    nickname: base.data.nickname,
    mobile: base.data.mobile,
    email: base.data.email,
    sex: base.data.sex == null ? 0 : Number(base.data.sex),
    areaCode: base.data.areaCode,
    areaZh: base.data.areaZh,
    address: base.data.address,
    longitude: base.data.longitude == null ? undefined : Number(base.data.longitude),
    latitude: base.data.latitude == null ? undefined : Number(base.data.latitude),
    signature: base.data.signature
  })
  baseAreaArr.value = (base.data.areaCode || '').split(',').filter(Boolean)
  baseOpen.value = true
  nextTick(() => baseFormRef.value?.clearValidate())
}

/** 地图选点确认：坐标收进列精度；换了点位老地址就对不上，解析出地址就一起改掉 */
const handleBaseLocationConfirm = (point: any) => {
  baseForm.longitude = Number(Number(point.longitude).toFixed(6))
  baseForm.latitude = Number(Number(point.latitude).toFixed(6))
  if (point.address) baseForm.address = point.address
}

const submitBase = () => {
  baseFormRef.value?.validate(async (valid: any) => {
    if (!valid) return
    baseForm.areaCode = (baseAreaArr.value || []).join(',')
    baseForm.areaZh = resolveAreaCode(baseForm.areaCode)
    baseSubmitLoading.value = true
    try {
      await updateMyProfileApi({
        avatar: baseForm.avatar,
        nickname: baseForm.nickname,
        mobile: baseForm.mobile,
        email: baseForm.email,
        sex: baseForm.sex,
        areaCode: baseForm.areaCode,
        areaZh: baseForm.areaZh,
        address: baseForm.address,
        longitude: baseForm.longitude,
        latitude: baseForm.latitude,
        signature: baseForm.signature
      })
      ElMessage.success('修改成功')
      baseOpen.value = false
      // 回刷的是这一页那一份；右上角头像/昵称那是登录时的会话快照，不跟着动（重新登录才换）
      getBase()
    } catch (error) {
    } finally {
      baseSubmitLoading.value = false
    }
  })
}

/** 弹窗那份表单的干净起点：新增拿它当空表，修改拿它垫掉上一次编辑留下的那几格 */
const resetAddressForm = () => {
  Object.assign(addressForm, {
    id: undefined,
    userId: undefined,
    nickname: undefined,
    receiverName: undefined,
    receiverPhone: undefined,
    tag: undefined,
    areaCode: undefined,
    areaZh: undefined,
    detail: undefined,
    postalCode: undefined,
    longitude: undefined,
    latitude: undefined,
    isDefault: 0
  })
  areaArr.value = []
  addressFormRef.value?.resetFields()
}

/** 新增：收货人两格按本人填（多数人就是自己），归属这一格送登录 id */
const handleAddressAdd = async () => {
  if (!base.data.id) await getBase()
  resetAddressForm()
  addressForm.userId = base.data.id
  addressForm.receiverName = base.data.nickname || base.data.username
  addressForm.receiverPhone = base.data.mobile
  addressTitle.value = '添加收货地址'
  addressOpen.value = true
}

/**
 * 修改：照收货地址那页从详情接口回显一次（不拿列表那一行直接用）——
 * 这一趟顺带走了服务层的归属校验，别人名下的那条在这里就被挡下，不用等提交才发现。
 * userId 沿用详情带回的那一格（这一页不给人把地址挪到别人名下，服务端那一道也拦）。
 */
const handleAddressUpdate = async (row: any) => {
  resetAddressForm()
  try {
    const { data } = await detailShopAddressApi(row.id)
    Object.assign(addressForm, data)
    // area_code 是 "110000,110100,110101" 这种，split 回级联选择器要的数组
    areaArr.value = (data.areaCode || '').split(',').filter(Boolean)
    addressTitle.value = '修改收货地址'
    addressOpen.value = true
  } catch (error) {
  }
}

/** 地图选点确认：坐标收进列精度；换了点位老地址就对不上，解析出地址就一起改掉 */
const handleLocationConfirm = (point: any) => {
  addressForm.longitude = Number(Number(point.longitude).toFixed(6))
  addressForm.latitude = Number(Number(point.latitude).toFixed(6))
  if (point.address) addressForm.detail = point.address
}

const submitAddress = () => {
  addressFormRef.value?.validate(async (valid: any) => {
    if (!valid) return
    // 级联的 [省, 市, 区县] 存成一列 area_code，中文地名解析出来存 area_zh（同收货地址那页）
    addressForm.areaCode = (areaArr.value || []).join(',')
    addressForm.areaZh = resolveAreaCode(addressForm.areaCode)
    addressSubmitLoading.value = true
    try {
      if (addressForm.id !== undefined) {
        await updateShopAddressApi(addressForm)
        ElMessage.success('修改成功')
      } else {
        await addShopAddressApi(addressForm)
        ElMessage.success('新增成功')
      }
      addressOpen.value = false
      getAddressList()
    } catch (error) {
    } finally {
      addressSubmitLoading.value = false
    }
  })
}

/**
 * 删除：走收货地址那页同一条 DELETE /shop/address/delete/{ids}（数组和单个 id 都吃，这里一次一条）。
 * 删掉的是默认那条不会自动补选 —— 服务层刻意不猜，下单时没有默认地址就让用户自己挑。
 */
const handleAddressDelete = (row: any) => {
  ElMessageBox.confirm(`确认删除「${row.receiverName}」的收货地址吗？`, '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    await deleteShopAddressApi(row.id)
    ElMessage.success('删除成功')
    // 删完这一页可能正好把当前那页删空，退回一页免得对着个空列表
    if (address.rows.length === 1 && address.query.pageNum > 1) address.query.pageNum -= 1
    getAddressList()
  }).catch(() => { })
}

/**
 * 广场消息「修改」：这一档只让人动 置顶 / 推荐 / 上下架 三格，但服务端那一条 SQL 认的是白名单七格一起写
 * （标题/正文/摘要/广场消息图都在里面，不递就是把它抹平），所以点开先捞一趟详情把原值带回来再提交。
 * 弹窗里不铺正文输入框：那是作者说的话，要改回「广场消息」那一页拒绝或下架，让他自己在广场改。
 */
const handlePlazaUpdate = async (row: any) => {
  Object.assign(plazaForm, {
    id: row.id,
    title: row.title,
    plazaImage: row.plazaImage || '',
    auditStatus: row.auditStatus,
    isTop: Number(row.isTop ?? 0),
    isRecommend: Number(row.isRecommend ?? 0),
    status: Number(row.status ?? 1)
  })
  plazaOpen.value = true
  plazaDetailLoading.value = true
  try {
    const { data } = await detailShopPlazaApi(row.id)
    if (!data) {
      plazaOpen.value = false
      return
    }
    plazaForm.title = data.title || ''
    plazaForm.content = data.content || ''
    plazaForm.plazaImage = data.plazaImage || ''
  } catch (error) {
    plazaOpen.value = false
  } finally {
    plazaDetailLoading.value = false
  }
}

const submitPlaza = async () => {
  plazaSubmitLoading.value = true
  try {
    await updateShopPlazaApi({
      id: plazaForm.id,
      title: plazaForm.title,
      plazaImage: plazaForm.plazaImage,
      content: plazaForm.content,
      isTop: plazaForm.isTop,
      isRecommend: plazaForm.isRecommend,
      status: plazaForm.status
    })
    ElMessage.success('修改成功')
    plazaOpen.value = false
    getPlazaList()
  } catch (error) {
  } finally {
    plazaSubmitLoading.value = false
  }
}

/**
 * 删除：走「广场消息」那一页同一条 DELETE /sys/plaza/delete/{ids}（一次一条）。
 * 软删，名下收到的赞/评论/分享行不回收；这一档不按审核状态拦，待审核的也删得掉。
 */
const handlePlazaDelete = (row: any) => {
  ElMessageBox.confirm(`确认删除广场消息「${row.title || row.contentText || '（没填标题）'}」吗？`, '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    await deleteShopPlazaApi(row.id)
    ElMessage.success('删除成功')
    if (plaza.rows.length === 1 && plaza.query.pageNum > 1) plaza.query.pageNum -= 1
    getPlazaList()
  }).catch(() => { })
}

/** 性别和状态在用户管理那页也是写死的（这两列没有字典），照抄那页的口径 */
const sexText = (value: any) => ({ 1: '男', 2: '女', 0: '保密' } as Record<number, string>)[Number(value)] ?? '保密'

const priceText = (amount: any) => (amount == null || amount === '' ? '-' : `￥${Number(amount).toFixed(2)}`)

/** 一位小数以下的数别拖一串 0：20.50 写成 20.5 */
const numText = (value: any) => {
  const n = Number(value)
  if (!Number.isFinite(n)) return ''
  return String(Number(n.toFixed(2)))
}

/** 积分是整数，空格子（脏数据）当 0 说，别让那五个数跳出一片空白 */
const pointsText = (value: any) => {
  const count = Number(value)
  return Number.isFinite(count) ? String(count) : '0'
}

/** 流水里的变动数：库里就是带符号存的（获取正、消耗负），补个正号让进出一眼分开 */
const signedText = (value: any) => {
  const count = Number(value)
  if (!Number.isFinite(count)) return '0'
  return count > 0 ? `+${count}` : `${count}`
}

/** 钱包流水里的变动数：同样带符号存，这里是钱，所以摆成金额（收入 +¥N、支出 -¥N） */
const signedAmount = (value: any) => {
  const amount = Number(value)
  if (!Number.isFinite(amount)) return '¥0.00'
  const text = `¥${Math.abs(amount).toFixed(2)}`
  return amount > 0 ? `+${text}` : amount < 0 ? `-${text}` : text
}

const TYPE_DISCOUNT = 2
const TYPE_FREE_SHIPPING = 3

const withCap = (row: any, text: string) => {
  const cap = Number(row.maxDiscount)
  return cap > 0 ? `${text}（最高抵 ${numText(cap)} 元）` : text
}

/**
 * 优惠那一格说成人话：满 100 减 20 / 9折 / 免配送费。
 * 读的都是这一行自己那几格快照（发放时定格的），所以模板后来改了面额也不会串到已发出去的券上。
 */
const amountText = (row: any) => {
  const type = Number(row.couponType)
  if (type === TYPE_FREE_SHIPPING) return withCap(row, '免配送费')
  const face = numText(row.faceValue)
  if (!face || Number(face) <= 0) return '-'
  if (type === TYPE_DISCOUNT) return withCap(row, `${numText(Number(row.faceValue) * 10)}折`)
  const threshold = Number(row.thresholdAmount) || 0
  return withCap(row, threshold > 0 ? `满 ${numText(threshold)} 减 ${face}` : `无门槛减 ${face}`)
}

onMounted(() => {
  loadTab(activeTab.value)
})
</script>

<style scoped lang="scss">
/* 签到记录那一颗摆在 tab 上面那一行：它作用的不是某一个 tab，所以不塞进任何一格里面 */
.page-toolbar {
  display: flex;
  align-items: center;
  margin-bottom: 12px;

  .page-tip {
    margin-left: 12px;
    color: var(--el-text-color-secondary);
    font-size: 12px;
  }
}

/* tab 里的内容和卡片边之间留一格：八个 pane 共用这一个类 */
.pane {
  padding-top: 4px;
}

/* 筛选项放在表格上面那一行（操作/筛选都在它作用的那张表上面，同别的列表页的 toolbar 位置） */
.pane-toolbar {
  display: flex;
  align-items: center;
  margin-bottom: 12px;

  .pane-filter {
    width: 160px;
  }

  .pane-tip {
    margin-left: 12px;
    color: var(--el-text-color-secondary);
    font-size: 12px;
  }
}

/* 弹窗里那一句说明：把这一笔会改成哪几个数说在按钮上面，别让人按完了才知道 */
.dialog-tip {
  margin: 4px 0 0;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

/* 经纬度两个输入框并成一格（同收货地址那页的新增弹窗） */
.pair-field {
  display: flex;
  align-items: center;
  width: 100%;

  .pair-split {
    padding: 0 8px;
    color: var(--el-text-color-secondary);
  }
}

/* 积分/钱包账户那一块和下面的流水表之间隔开一格 */
.points-account,
.wallet-account {
  margin-bottom: 16px;
}

.balance {
  font-weight: 600;
  color: var(--el-color-primary);
}

.points-warning {
  color: var(--el-color-warning);
}

.points-in {
  color: var(--el-color-success);
}

.points-out {
  color: var(--el-color-danger);
}

/* 钱包那两格：冻结中（有钱被没支付的单占着）标黄，流水里的收入绿、支出红 */
.amount-warning {
  color: var(--el-color-warning);
}

.amount-in {
  color: var(--el-color-success);
}

.amount-out {
  color: var(--el-color-danger);
}

.pay-amount {
  font-weight: 600;
}

/* 徽章那一格的形状在 components/BadgeView 那一处，这一页不再自己写 */
</style>
