/** 地图选点抛回来的那三个数（MapPicker 的 confirm、地址表单的 handlePick 共用这一份形状） */
export interface PickedPoint {
  longitude: number;
  latitude: number;
  /** 高德按坐标反推出来的详细地址；只是打开看了眼位置时为空串，调用方据此决定要不要覆盖手写的详细地址 */
  address: string;
}
