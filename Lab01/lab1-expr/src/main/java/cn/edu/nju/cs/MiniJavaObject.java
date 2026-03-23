package cn.edu.nju.cs;

public class MiniJavaObject {
    // 类型
    public String type;

    // 值
    public Object value;

    // 构造函数
    public MiniJavaObject(String ty, Object val) {
        this.type = ty;
        this.value = val;
    }
    
    // 获取值
    public Object getValue() {
        return value;
    }
}
