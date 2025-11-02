package io.sdkman.model;

/**
 * JDK列表项包装类
 * 可以表示供应商标题或JDK版本
 */
public class JdkListItem {
    private final ItemType type;
    private final String vendorName;
    private final SdkVersion sdkVersion;
    private final boolean recommended;  // 是否推荐

    /**
     * 创建供应商标题项
     */
    public static JdkListItem createVendorHeader(String vendorName) {
        return createVendorHeader(vendorName, false);
    }

    /**
     * 创建供应商标题项（带推荐标识）
     */
    public static JdkListItem createVendorHeader(String vendorName, boolean recommended) {
        return new JdkListItem(ItemType.VENDOR_HEADER, vendorName, null, recommended);
    }

    /**
     * 创建JDK版本项
     */
    public static JdkListItem createJdkVersion(SdkVersion sdkVersion) {
        return new JdkListItem(ItemType.JDK_VERSION, null, sdkVersion, false);
    }

    private JdkListItem(ItemType type, String vendorName, SdkVersion sdkVersion, boolean recommended) {
        this.type = type;
        this.vendorName = vendorName;
        this.sdkVersion = sdkVersion;
        this.recommended = recommended;
    }

    public ItemType getType() {
        return type;
    }

    public String getVendorName() {
        return vendorName;
    }

    public SdkVersion getSdkVersion() {
        return sdkVersion;
    }

    public boolean isRecommended() {
        return recommended;
    }

    public enum ItemType {
        VENDOR_HEADER,  // 供应商标题
        JDK_VERSION     // JDK版本
    }
}
