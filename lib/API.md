# libstrapper API

`top.x0a` 0.4.0 —— WEB API 参数契约校验 + 独立的 JSON 解析/写出

> 本文件由源码注释自动生成（`./gradlew apiDoc`），请勿手工编辑。

## 目录

- **`top.x0a.json`**
  - [`Json`](#topx0ajsonjson) —— 极简 JSON 解析与序列化，零第三方依赖
  - [`JsonWriter`](#topx0ajsonjsonwriter) —— 把 key-value 结构写成 JSON 串
- **`top.x0a.strapper`**
  - [`ErrorCode`](#topx0astrappererrorcode) —— 校验过程中可能产生的问题码
  - [`FormatException`](#topx0astrapperformatexception) —— 格式描述本身写错时抛出
  - [`JsonReader`](#topx0astrapperjsonreader) —— JSON 解析的插拔点
  - [`KeyFormat`](#topx0astrapperkeyformat) —— 一个 key 的格式描述，对应 valueFormat.md / dataStruct.md 里 key 上可以带的标记：
  - [`Requirement`](#topx0astrapperrequirement) —— 参数项的必填性，对应格式描述开头的 `*` / `!` / 无标记
  - [`StrapIssue`](#topx0astrapperstrapissue) —— 一条校验问题
  - [`StrapResult`](#topx0astrapperstrapresult) —— 一次校验的结果
  - [`Strapper`](#topx0astrapperstrapper) —— WEB API 参数契约校验器 —— libstrapper 的 Java 实现
  - [`Types`](#topx0astrappertypes) —— 类型表 —— 本库的扩展点
  - [`ValueFormat`](#topx0astrappervalueformat) —— 一条值的格式描述，对应 valueFormat.md 的语法：

---

## 包 `top.x0a.json`

### `top.x0a.json.Json`

```java
public final class Json
```

极简 JSON 解析与序列化，零第三方依赖。

这个类不依赖 `top.x0a.strapper` 里的任何东西，可以单独拿去用。
它严格按 RFC 8259 解析，不接受单引号、尾逗号、注释，也不允许文档结束后还有多余内容 ——
宽松解析对参数校验这类场景是安全弱化，所以刻意不做。

解析结果使用 JDK 原生类型表达，便于直接消费：

- object → `LinkedHashMap`（保持书写顺序，格式描述里 key 的先后有意义）
- array  → `ArrayList`
- string → `String`
- number → `Long`（整数）或 `Double`（含小数点/指数）
- true/false → `Boolean`，null → `null`

#### 方法

```java
public static Object parse(String text)
```

解析 JSON 文本。

- **参数** `text` —— 完整的 JSON 文档
- **返回** Map / List / String / Long / Double / Boolean / `null` 构成的树
- **抛出** `JsonException` —— 文本不是合法 JSON；消息里带出错位置

```java
public static String write(Object value)
```

紧凑输出。等价于 `JsonWriter.compact().write(value)`。

```java
public static String writePretty(Object value)
```

缩进输出，便于人读。等价于 `JsonWriter.pretty().write(value)`。

#### `top.x0a.json.Json.JsonException`

```java
public static final class JsonException
```

JSON 语法错误。消息中带出错位置，便于定位格式描述里的笔误。

##### 方法

```java
public int position()
```

### `top.x0a.json.JsonWriter`

```java
public final class JsonWriter
```

把 key-value 结构写成 JSON 串。零第三方依赖，不依赖 `top.x0a.strapper`。

两种用法：

**一、已经有一棵 Map / List 树**

```java
Map<String, Object> body = new LinkedHashMap<>();
body.put("code", "ok");
body.put("data", List.of(1, 2, 3));

String json = JsonWriter.compact().write(body);
JsonWriter.pretty().writeTo(out, body);   // out 是任意 Appendable：Writer、StringBuilder……
```

**二、流式拼，不必先攒一棵树**

```java
// out 是这次输出专属的 Writer / StringBuilder，不与别的线程共享
JsonWriter.compact().stream(out, json -> {
    json.beginObject()
        .field("code", "ok")
        .name("data").beginArray();
    for (Order order : orders) {
        json.beginObject()
            .field("id", order.id())
            .field("amount", order.amount())
            .endObject();
    }
    json.endArray().endObject();
});
```

适合结果集很大或者边查边写的场景 —— 整个过程不会在内存里出现完整的结果串。

**能写哪些类型**

- `null`、`CharSequence`、`Character`、`Boolean`、`Number`
- `Map` → object（key 一律按 `String.valueOf` 取字符串）
- `Iterable`、Java 数组（含基本类型数组）→ array
- `Enum` → 它的 `name()`
- 其他类型 → 退化成它的 `toString()`，当字符串写。
  想让这种情况直接报错，用 `strict(true)`

**线程安全**

`JsonWriter` 本身不可变（字段全 `final` 的 `final` 类），
配置方法返回新实例，可以做成常量在多线程间共享，不需要任何同步。

`Stream` 则是**线程封闭**的：它有写到一半的状态，只能由创建它的线程使用。
这不是靠约定 —— 别的线程一碰就会抛 `IllegalStateException`
（确实需要跨线程接力时用 `Stream.handOff()`）。

还有一点容易漏：**目标 `Appendable` 也必须是这个线程独占的**。
两个线程各拿各的 `Stream` 往同一个 `StringBuilder` 里写，一样会写坏 ——
`StringBuilder` 自己就不是线程安全的，内容会交错甚至丢字符。
多个线程要往同一个目标汇聚时，正确做法是各自在本地拼完整，再整篇一次性追加：

```java
String doc = JsonWriter.compact().write(payload);      // 本地拼完，互不干扰
synchronized (sharedOut) { sharedOut.append(doc); }    // 加锁的粒度是一整篇文档
```

加锁粒度必须是整篇文档，不能是单个方法 —— JSON 的原子单位是文档，不是一次写入。

#### 方法

```java
public static JsonWriter compact()
```

紧凑输出，不换行。接口响应用这个。

```java
public JsonWriter escapeNonAscii(boolean on)
```

把非 ASCII 字符写成 `\\uXXXX`。

输出全是 ASCII，就不会再因为响应头里的 charset 写错而乱码；代价是中文内容体积翻几倍。

```java
public static String number(Number n)
```

数字的 JSON 形式。

`double` / `float` 写成不带指数的形式，避免 `1.0E10` 这种
对前端不友好的输出；NaN 和 Infinity 不是合法 JSON，写成 `null`。

```java
public static String number(double d)
```

`double` 的 JSON 形式，不带指数。

```java
public static JsonWriter pretty()
```

缩进 2 空格的输出，给人看的。

```java
public static JsonWriter pretty(int spaces)
```

指定缩进宽度；传 0 等同于 `compact()`。

```java
public JsonWriter skipNulls(boolean on)
```

对象里值为 `null` 的 key 直接不写出来。数组里的 null 不受影响。

```java
public void stream(Appendable out, Consumer<JsonWriter.Stream> body)
```

边拼边往 `out` 里写，写完自动收尾。

推荐用这个而不是 `stream(Appendable)`：`Stream` 出不了这个 lambda，
也就没机会被别的线程拿到，线程封闭是结构上保证的。

- **参数** `out` —— 这次输出专属的目标，不能与别的线程共享
- **参数** `body` —— 往 `Stream` 里拼内容；返回时括号必须都闭合了

```java
public String stream(Consumer<JsonWriter.Stream> body)
```

拼进一个自己管的缓冲区，返回结果串。连目标 `Appendable` 都不用操心。

- **参数** `body` —— 往 `Stream` 里拼内容；返回时括号必须都闭合了

```java
public JsonWriter.Stream stream(Appendable out)
```

开一个流式写出器，边拼边往 `out` 里写。

用完要 `Stream.close()`（配合 try-with-resources），close 时会检查括号是否都闭合了。
需要把写出过程拆散到几个方法里时用它，否则优先用
`java.util.function.Consumer)` —— 那个形式不会让 `Stream` 泄漏出去。

- **参数** `out` —— 这次输出专属的目标，不能与别的线程共享

```java
public JsonWriter strict(boolean on)
```

遇到不认识的类型时直接抛 `IllegalArgumentException`，而不是退化成 `toString()`。

输出的是接口响应时建议打开：一个本该是数字的字段悄悄变成了对象的 toString，
比当场报错难查得多。

```java
public String write(Object value)
```

写成字符串。

```java
public void writeTo(Appendable out, Object value) throws IOException
```

直接写进 `Appendable`（`Writer`、`StringBuilder` 都可以），不攒中间串。

#### `top.x0a.json.JsonWriter.Stream`

```java
public static final class Stream
```

流式写出器。不必先攒一棵 Map / List 树，适合结果集很大或者边查边写的场景。

**线程封闭**：一个实例只能由创建它的线程使用。别的线程一碰就抛
`IllegalStateException`，不会让你拿到一段悄悄写坏的 JSON。
确实需要换线程接着写时，先由当前线程调 `handOff()`。

注意目标 `Appendable` 也必须是这个线程独占的，原因见 `JsonWriter` 的线程安全一节。

##### 方法

```java
public JsonWriter.Stream beginArray()
```

开始一个数组。

```java
public JsonWriter.Stream beginObject()
```

开始一个对象。

```java
public void close()
```

收尾。检查括号是否都闭合了；`out` 如果是 `Closeable` 本身，
这里不会去关它 —— 谁开的谁负责关。

```java
public JsonWriter.Stream endArray()
```

结束当前数组。

```java
public JsonWriter.Stream endObject()
```

结束当前对象。

```java
public JsonWriter.Stream field(String name, Object value)
```

`name(String)` + `value(Object)`，对象里最常用的写法。

```java
public JsonWriter.Stream handOff()
```

交出归属，让另一个线程接着写。

必须由当前归属线程调用。交接是通过一次 volatile 写完成的，所以接手的线程
一定能看到前面写进去的全部内容 —— 这是真正的 happens-before 保证，不只是放行检查。

目标 `Appendable` 的可见性得你自己保证：它也要跟着一起交接，
不能两个线程同时拿着它。

```java
public JsonWriter.Stream name(String name)
```

写一个 key，之后必须紧跟一个值。

```java
public JsonWriter.Stream nullValue()
```

写一个 JSON null。

```java
public Thread owner()
```

当前归属线程；已交接、还没人认领时返回 `null`。

```java
public JsonWriter.Stream value(Object value)
```

写一个值。对象、数组、纯量都行，会按 `JsonWriter` 的规则整棵写出去。

---

## 包 `top.x0a.strapper`

### `top.x0a.strapper.ErrorCode`

```java
public enum ErrorCode
```

校验过程中可能产生的问题码。

前缀含义：

- `FORMAT_*` —— 格式描述本身写错了（开发期问题，应当在接口文档评审时就消灭）
- `DATA_*`   —— 格式描述没问题，是传入的数据不符合约定（运行期问题）
- `TYPE_*`   —— 类型表里找不到可用的类型或校验方法

`*_JSON_STRUCT_ERROR` 与 `*_JSON_READER_ERROR` 的区别：

- 用内置解析器时，失败只可能是 JSON 语法问题，报 `*_JSON_STRUCT_ERROR`
- 换了 `JsonReader` 之后，失败原因本库无从知道 —— 可能是语法错，也可能是
  适配器自己的策略把它挡了（比如请求体超限）—— 所以报 `*_JSON_READER_ERROR`，
  真实原因在 `StrapIssue.detail()` 里

#### 枚举常量

| 常量 | 说明 |
|---|---|
| `FORMAT_JSON_STRUCT_ERROR` | 格式描述的 JSON 字符串有语法错误 |
| `FORMAT_JSON_READER_ERROR` | 读取格式描述时，外部 JSON 解析器报错 |
| `FORMAT_SYNTAX_ERROR` | 格式描述有语法错误 |
| `FORMAT_VALUEFORMAT_SYNTAX_ERROR` | 格式描述的参数值有语法错误 |
| `FORMAT_KEY_SYNTAX_ERROR` | 格式描述的 KEY 有语法错误 |
| `FORMAT_RANGE_SYNTAX_ERROR` | 格式描述的取值范围有语法错误 |
| `FORMAT_UNKNOWN_KEYTYPE_ERROR` | 格式描述中指定的数据类型未定义 |
| `FORMAT_TYPE_DISABLED` | 格式描述中用到了被本项目禁用的数据类型 |
| `FORMAT_NOT_SUPPORT_MULTIFORMAT_ARRAY` | 格式描述中的数组只能有且只有1个格式 |
| `FORMAT_NOT_SUPPORT_NOFORMAT_ARRAY` | 格式描述中的数组使用了不支持的格式 |
| `FORMAT_DEFAULT_VALUE_INVALID` | 格式描述中的默认值本身不符合该格式 |
| `DATA_JSON_STRUCT_ERROR` | 传入数据的 JSON 字符串有语法错误 |
| `DATA_JSON_READER_ERROR` | 读取传入数据时，外部 JSON 解析器报错 |
| `DATA_NOT_MATCHED` | 传入数据格式不匹配 |
| `DATA_NOT_IN_VALID_RANGE` | 传入数据不在合理的范围 |
| `DATA_NOT_IN_SET_RANGE` | 传入数据不符合要求的范围 |
| `DATA_OVER_LENGTH` | 传入数据超过了格式约定的长度 |
| `DATA_ARRAY_LENGTH_ERROR` | 传入数组的长度不符合格式约定 |
| `DATA_KEY_NEED_EXIST` | 传入数据缺少了必填的KEY |
| `DATA_NOT_EXIST` | 传入数据的KEY不存在 |
| `DATA_UNKNOWN_KEY_ERROR` | 传入数据存在格式中不存在的参数 |
| `DATA_USE_DEFAULT_VALUE` | 传入数据无效，已使用格式约定的默认值 |
| `TYPE_NO_MATCHED` | 没有匹配的类型 |
| `TYPE_WITHOUT_METHOD` | 没有匹配的解析方法 |

#### 方法

```java
public String message()
```

这个问题码的中文说明。

### `top.x0a.strapper.FormatException`

```java
public final class FormatException
```

格式描述本身写错时抛出。由 `Strapper` 捕获并转成 `StrapIssue`。

#### 构造方法

```java
public FormatException(ErrorCode code, String detail)
```

#### 方法

```java
public ErrorCode code()
```

```java
public String detail()
```

去掉前缀后的补充说明。

### `top.x0a.strapper.JsonReader`

```java
public interface JsonReader
```

JSON 解析的插拔点。

本库只在两处需要 JSON 解析（`Strapper.strap` 和 `Strapper.checkFormat` 各一次），
所以这个接口只有一个方法。默认用内置的 `Json`，想换成项目里已经在用的第三方库就传一个适配器进来，
这样一个进程里不必同时驮着两套 JSON 解析实现。

**约定**

`read` 必须把 JSON 文本解析成这样一棵通用树：

- object → `Map`，**并且保持 key 的书写顺序**（校验结果按这个顺序产出）
- array → `List`
- string → `String`，true/false → `Boolean`，null → `null`
- number → 任意 `Number`（`Integer` / `Long` / `Double` /
  `BigDecimal` 都行，本库按它的字符串形式做校验）

解析失败时抛 `JsonReadException`；用 `wrapping` 包一层会自动完成转换。

**写第三方适配器**

```java
ObjectMapper mapper = new ObjectMapper();
strapper.jsonReader(JsonReader.wrapping(t -> mapper.readValue(t, Object.class)));   // Jackson

Gson gson = new Gson();
strapper.jsonReader(JsonReader.wrapping(t -> gson.fromJson(t, Object.class)));      // Gson

strapper.jsonReader(JsonReader.wrapping(JSON::parse));                              // fastjson2
```

**换之前要知道的三件事**

1. **Gson 把所有数字都解析成 `Double`**，超过 2^53 的整数会丢精度：
  `1234567890123456789` 会变成 `1234567890123456800`，
  校验照样能过，值已经错了。ID 类字段用 Gson 后端时要留意。
2. **各家的宽松度不同**：Jackson 默认接受文档尾部的多余内容，
  Gson 和 fastjson2 默认接受单引号，fastjson2 还接受尾逗号，
  Gson 和 fastjson2 对空串返回 `null` 而不是报错。
  内置的 `Json` 这些一律拒绝 —— 换后端等于放松了这一层把关。
3. **只有读是可换的，写不换**：`StrapResult.toJson()` 始终用内置的写出实现，
  因为它写的是本库自己产出的 Map/List，不涉及第三方的对象模型。

#### 方法

```java
static JsonReader builtin()
```

内置实现，零第三方依赖，严格按 RFC 8259 解析。这是默认值。

```java
Object read(String text)
```

把 JSON 文本解析成 Map / List / String / Number / Boolean / null 构成的树。

- **抛出** `JsonReadException` —— 文本不是合法 JSON

```java
static JsonReader wrapping(JsonReader.Parser parser)
```

把任意第三方解析器包成 `JsonReader`：它自己抛什么异常都会被转成
`JsonReadException`，本库只认这一种。

#### `top.x0a.strapper.JsonReader.JsonReadException`

```java
final class JsonReadException
```

解析失败。适配层用 `wrapping` 时会自动把第三方的异常转成这个。

##### 构造方法

```java
public JsonReadException(String message)
```

```java
public JsonReadException(String message, Throwable cause)
```

#### `top.x0a.strapper.JsonReader.Parser`

```java
interface Parser
```

允许抛受检异常的解析函数，方便直接写 `t -> mapper.readValue(t, Object.class)`。

##### 方法

```java
Object parse(String text) throws Exception
```

#### `top.x0a.strapper.JsonReader.Builtin`

```java
final class Builtin
```

持有内置实现的单例，避免每次 `builtin()` 都新建一个 lambda。

### `top.x0a.strapper.KeyFormat`

```java
public record KeyFormat(String source, KeyFormat.Kind kind, Requirement requirement, String name, Integer minItems, Integer maxItems, String keyType)
```

一个 key 的格式描述，对应 valueFormat.md / dataStruct.md 里 key 上可以带的标记：

```java
"[*|!][[n]|[n,m]]<key名>"
```

- `"*carData"` —— 值为对象或数组时，必填性标在 key 上（dataStruct 特殊情况二）
- `"*[4,9]fruits"` —— 必填，且数组长度在 4~9 之间；`"[n]"` 表示长度恰为 n
- `"..."` —— 本级接受未在格式中列出的 key（`Kind.MORE_KEYS`）
- `"!string"` —— key 名本身不确定，只约定 key 要符合某个类型（`Kind.WILDCARD`）

#### 记录组件

| 组件 | 类型 | 说明 |
|---|---|---|
| `source` | `String` | 原始 key 串 |
| `kind` | `KeyFormat.Kind` | 这个 key 的种类 |
| `requirement` | `Requirement` | 必填性 |
| `name` | `String` | 普通 key 的名字；`MORE_KEYS`/`WILDCARD` 时无意义 |
| `minItems` | `Integer` | 值为数组时的最小长度，未约定为 `null` |
| `maxItems` | `Integer` | 值为数组时的最大长度，未约定为 `null` |
| `keyType` | `String` | `WILDCARD` 时，key 名自身要满足的类型名 |

#### 常量

| 名称 | 类型 | 说明 |
|---|---|---|
| `MORE_KEYS_TOKEN` | `String` | 允许出现未定义 key 的特殊 key。 |

#### 方法

```java
public boolean acceptsSize(int size)
```

数组长度是否落在约定内。

```java
public boolean hasItemsRange()
```

是否对数组长度做了约定。

```java
public String itemsRangeText()
```

人读的长度约定描述，用于错误信息。

```java
public static KeyFormat parse(String source, Types types)
```

解析一个 key。

`!` 在 key 上有两种读法：`!string` 是"key 名不确定"（dataStruct），
而 `!mobileVerified` 是"条件必填"（valueFormat）。消歧规则：
**标记为 `!`、没有数组长度约束、且剩余部分恰好是一个已注册的类型名** 时按通配 key 处理，
否则按条件必填的普通 key 处理。

- **参数** `types` —— 用于判断 `!xxx` 里的 xxx 是不是类型名；传 `null` 则一律按普通 key 处理

#### `top.x0a.strapper.KeyFormat.Kind`

```java
public static enum Kind
```

key 的种类。

##### 枚举常量

| 常量 | 说明 |
|---|---|
| `NORMAL` | 普通 key。 |
| `MORE_KEYS` | `"..."`：允许出现未定义的 key。 |
| `WILDCARD` | `"!<类型>"`：key 名不确定，只约束 key 的类型。 |

### `top.x0a.strapper.Requirement`

```java
public enum Requirement
```

参数项的必填性，对应格式描述开头的 `*` / `!` / 无标记。

#### 枚举常量

| 常量 | 说明 |
|---|---|
| `OPTIONAL` | 无标记：选填。 |
| `REQUIRED` | `*`：必填。 |
| `CONDITIONAL` | `!`：根据前后文条件确定是否必填。 spec 明确这类条件"需要在具体情境下具体描述"，格式本身给不出答案， 因此本库把判断权交给调用方：注册 `ConditionalResolver` 即可； 不注册时按选填处理。 |

### `top.x0a.strapper.StrapIssue`

```java
public record StrapIssue(String path, ErrorCode code, String detail)
```

一条校验问题。

#### 记录组件

| 组件 | 类型 | 说明 |
|---|---|---|
| `path` | `String` | 出问题的位置，形如 `$.data.list[0].modelId`；根为 `$` |
| `code` | `ErrorCode` | 问题码 |
| `detail` | `String` | 针对本次问题的补充说明（可能为空串） |

### `top.x0a.strapper.StrapResult`

```java
public record StrapResult(boolean ok, Object value, List<StrapIssue> errors, List<StrapIssue> warnings)
```

一次校验的结果。

#### 记录组件

| 组件 | 类型 | 说明 |
|---|---|---|
| `ok` | `boolean` | 是否没有 error 级问题（warning 不影响 ok） |
| `value` | `Object` | 按格式描述取出并归一化后的数据；顶层是 `Map` 或 `List`，失败时可能为 `null` |
| `errors` | `List<StrapIssue>` | 导致校验失败的问题 |
| `warnings` | `List<StrapIssue>` | 不影响结果可用性、但值得知会的问题（用了默认值、丢弃了未定义的 key 等） |

#### 方法

```java
public List<Object> asList()
```

顶层为数组时的取值；不是数组则抛 `IllegalStateException`。

```java
public Map<String,Object> asMap()
```

顶层为对象时的取值；不是对象则抛 `IllegalStateException`。

```java
public Optional<Object> find(String path)
```

按路径取值，路径写法同错误信息里的 path，但不带开头的 `$.`：
`"data.list[0].modelId"`。取不到时返回空。

```java
public String report()
```

人读的问题清单，直接打到日志里就能定位。

```java
public String toJson()
```

取值的紧凑 JSON 形式。这里始终用内置的写出实现，与 `JsonReader` 换不换无关。

```java
public String toPrettyJson()
```

取值的缩进 JSON 形式。

### `top.x0a.strapper.Strapper`

```java
public final class Strapper
```

WEB API 参数契约校验器 —— libstrapper 的 Java 实现。

用一段 JSON 形态的"格式描述"去校验另一段 JSON 形态的"参数数据"，产出归一化后的取值与问题清单。
格式描述的语法见 spec 的 valueFormat.md 与 dataStruct.md。

**最简用法**

```java
String format = """
    {
      "username":"*string:32//用户名",
      "age":"int[0,150]#0//年龄",
      "gender":"string{男,女}//性别可以不填"
    }""";

StrapResult result = new Strapper().strap(format, requestBody);
if (!result.ok()) {
    return fail(result.report());
}
String username = (String) result.asMap().get("username");
```

**扩展自己领域的类型**

扩展类型就是一张「名 : 正则」的明文表，内置的 mobile、idcard 等只是预先写好的几行，
业务领域的类型走完全相同的入口：

```java
Strapper strapper = new Strapper()
        // 共享电动车领域：车辆编号 9 位数字，前 4 位是生产批次
        .addType("int.vehicleNo", "^20\\d{2}\\d{5}$")
        // 也可以整张表放在接口文档旁边一起维护
        .loadTypes(Path.of("conf/types.txt"));
// 格式描述里写 "*vehicleNo//车辆编号" 或 "*int.vehicleNo//车辆编号"
```

**换掉内置的 JSON 解析**

项目里已经在用 Jackson / Gson / fastjson2 时，不必再驮一套解析实现：

```java
strapper.jsonReader(JsonReader.wrapping(t -> mapper.readValue(t, Object.class)));
```

具体约定和几个要当心的差异见 `JsonReader`。

**线程安全**

配置完成后 `strap` 不改变实例状态（每次调用的状态都在局部），可以在多线程间共享。

但配置字段是非 `final` 的，`Types` 内部也是可变的 map，所以配置必须发生在
"发布给其他线程之前"：

```java
// 安全：静态初始化有 happens-before 保证
static final Strapper STRAPPER = new Strapper().addType(...).disableType("text");

// 危险：已经在跑请求了才改配置，别的线程可能看到半配好的状态
STRAPPER.disableType("text");
```

#### 构造方法

```java
public Strapper()
```

#### 方法

```java
public Strapper addType(String name, String regex)
```

加一条扩展类型。

- **参数** `name` —— 点分全名，形如 `int.vehicleNo`；点号前必须是基础类型名
- **参数** `regex` —— 该类型的正则

```java
public Strapper addTypes(Map<String,String> entries)
```

批量加扩展类型，键为全名，值为正则。

```java
public StrapResult checkFormat(String format)
```

只检查格式描述本身写得对不对，不需要数据。适合在接口文档的 CI 里跑。

- **返回** `StrapResult.value()` 为解析后的格式树

```java
public Strapper conditionalResolver(Strapper.ConditionalResolver resolver)
```

设置 `!` 条件必填的判定逻辑。

```java
public Strapper disableType(String name)
```

禁用一个类型，之后格式描述里写到它就会报错。

禁用基础类型会连带禁用从它派生的扩展类型。典型用法是
`disableType("text")`：不接受任何可能带制表符、换行符的参数，收紧攻击面。

```java
public Strapper enableType(String name)
```

解除禁用。

```java
public Strapper jsonReader(JsonReader reader)
```

换用另一个 JSON 解析器。默认是内置的零依赖实现。

项目里已经在用 Jackson / Gson / fastjson2 时，包一层传进来就不必驮两套解析实现：

```java
strapper.jsonReader(JsonReader.wrapping(t -> mapper.readValue(t, Object.class)));
```

换之前请先读 `JsonReader` 上「换之前要知道的三件事」，尤其是 Gson 的数字精度问题。

```java
public JsonReader jsonReader()
```

当前使用的 JSON 解析器。

```java
public Strapper loadTypes(String text)
```

从明文读入一张扩展类型表，格式见 `Types.load(String)`。

```java
public Strapper loadTypes(Path file) throws IOException
```

从文件读入一张扩展类型表，UTF-8 编码。

```java
public StrapResult strap(String format, String json)
```

用格式描述校验一段 JSON 数据。

- **参数** `format` —— 格式描述；空串按 `"{}"` 处理（dataStruct 特殊情况一）
- **参数** `json` —— 待校验的 JSON 文本

```java
public Strapper types(Types types)
```

换用另一张类型表。

```java
public Types types()
```

当前的类型表，可直接在上面继续 `add`。

```java
public Strapper unknownTypePolicy(Strapper.UnknownTypePolicy policy)
```

设置未定义类型的处理策略。

#### `top.x0a.strapper.Strapper.UnknownTypePolicy`

```java
public static enum UnknownTypePolicy
```

格式描述里出现未定义类型时的处理策略。

##### 枚举常量

| 常量 | 说明 |
|---|---|
| `ERROR` | 报 `ErrorCode.FORMAT_UNKNOWN_KEYTYPE_ERROR`，让类型名的笔误在开发期就暴露。默认值。 |
| `PASS_THROUGH` | 按 `text` 放行并记一条告警。PHP 版是这个行为，迁移期可用。 |

#### `top.x0a.strapper.Strapper.ConditionalResolver`

```java
public static interface ConditionalResolver
```

判定 `!` 标记的参数在当前上下文下到底必不必填。

spec 说这类条件"需要在具体情境下具体描述"，所以判断权在调用方。

##### 方法

```java
boolean isRequired(String path, String keyName, Map<String,Object> siblings)
```

- **参数** `path` —— 该参数的完整路径，如 `$.data.invoiceTitle`
- **参数** `keyName` —— 该参数的 key 名
- **参数** `siblings` —— 同一层已知的原始数据（只读），可据此判断

### `top.x0a.strapper.Types`

```java
public final class Types
```

类型表 —— 本库的扩展点。

类型分两层：

1. **基础类型**固定 9 个，决定一个值最终被解析成什么（`Kind`）：
  `int`、`float`、`number`、`bool`、
  `string`、`text`、`date`、`time`、`datetime`。
2. **扩展类型**就是一张「名 : 正则」的明文表，名字写成
  `<基础类型>.<扩展名>`，点号前面的部分说明这个值按什么语义取。
  一个值要先过基础类型的正则，再过扩展类型的正则。

spec 里列的 mobile、idcard、retCode…… 本身就是这么写出来的，内置表见
`top/x0a/strapper/types.txt`。业务领域的类型走完全相同的入口：

```java
// 共享电动车领域：车辆编号 9 位数字，前 4 位是生产批次
types.add("int.vehicleNo", "^20\\d{2}\\d{5}$");

// 或者整张表从明文里读进来，跟接口文档放在一起维护
types.load(Path.of("conf/types.txt"));
```

格式描述里写全名 `"*int.vehicleNo"` 或短名 `"*vehicleNo"` 都可以；
短名在全表内唯一时才能用，重名时必须写全名，避免歧义静默通过。
同名再写一次即为覆盖，不满意内置规则就重写同名一行。

**string 与 text 的分工**

`string` 是纯字符串，不允许出现制表符、换行符这类转义字符；`text` 允许。
内置表里凡是单行短串一律挂在 `string` 下，只有确实可能带换行的才挂到 `text` 下。
这样需要收紧攻击面时，一句 `disable("text")` 就能把全部可带换行的
类型一次性关掉：

```java
Types types = Types.standard().disable("text");   // 之后格式描述里写 text 或 text.* 一律报错
```

#### 常量

| 名称 | 类型 | 说明 |
|---|---|---|
| `BUILTIN_RESOURCE` | `String` | 内置扩展类型表所在的资源路径。 |

#### 方法

```java
public Types add(String name, String regex)
```

加一条扩展类型。

- **参数** `name` —— 点分全名，形如 `string.email`；点号前必须是基础类型名
- **参数** `regex` —— 该类型的正则；值要同时满足基础类型的正则和它

```java
public Types addAll(Map<String,String> entries)
```

批量加，键为全名，值为正则。

```java
public static Types base()
```

只有基础类型，扩展类型从零开始自己写。

```java
public static Set<String> baseNames()
```

全部基础类型名。

```java
public Types copy()
```

复制一份，之后的改动互不影响。

```java
public Types disable(String name)
```

禁用一个类型。之后格式描述里再写到它就会报
`ErrorCode.FORMAT_TYPE_DISABLED`。

禁用基础类型会连带禁用从它派生的全部扩展类型 —— `disable("text")`
就是一次性关掉所有可能带换行、制表符的参数类型，需要收紧攻击面时很有用。

名字写错会直接抛异常而不是静默失败：安全相关的开关，悄悄没生效比报错危险得多。

- **参数** `name` —— 基础类型名、扩展类型全名或唯一短名

```java
public Set<String> disabledTypes()
```

当前被显式禁用的类型名。

```java
public Types enable(String name)
```

解除禁用。

```java
public Map<String,String> extendedTypes()
```

全部扩展类型：全名 -> 正则原文。

```java
public boolean has(String name)
```

是否可解析。

```java
public boolean isDisabled(String name)
```

该类型是不是被禁用了（基础类型被禁用时，从它派生的扩展类型也算被禁用）。

```java
public Types load(String text)
```

从明文读入一张类型表。每行一条 `名 : 正则`，
`#` 开头的行是注释，空行忽略；名字与正则之间用第一个 `:` 或 `=` 分隔。

```java
public Types load(Path file) throws IOException
```

从文件读入一张类型表，UTF-8 编码。

```java
public static String match(Types.TypeDef def, String raw)
```

先过基础类型的正则，再过扩展类型的正则。

- **返回** 通过返回 `null`，否则返回失败原因

```java
public Types.TypeDef resolve(String name)
```

按基础类型名、扩展类型全名或唯一短名查找。
找不到、短名有歧义、或该类型已被禁用时返回 `null`。

```java
public String resolveProblem(String name)
```

`resolve` 失败的原因，用于把「未定义」和「短名有歧义」区分开。

```java
public static Types standard()
```

基础类型 + 内置扩展类型表（spec 中列出的全部扩展类型）。

```java
public String text()
```

把当前扩展类型表导出成明文，可直接落盘或打印。

#### `top.x0a.strapper.Types.Kind`

```java
public static enum Kind
```

基础类型决定一个值最终被解析成什么。

##### 枚举常量

| 常量 | 说明 |
|---|---|
| `INT` | 整数，结果为 `Long`。 |
| `DECIMAL` | 小数，结果为 `Double`。 |
| `BOOL` | 布尔，结果为 `Boolean`。 |
| `TEXT` | 文本，结果为 `String`。 |
| `DATE` | 日期，结果为归一化的 `yyyy-MM-dd`。 |
| `TIME` | 时间，结果为归一化的 `HH:mm:ss`。 |
| `DATETIME` | 日期时间，结果为归一化的 `yyyy-MM-dd HH:mm:ss`。 |

#### `top.x0a.strapper.Types.TypeDef`

```java
public static record TypeDef(String name, String base, Types.Kind kind, Pattern basePattern, Pattern pattern)
```

一个可用的类型：基础类型本身，或一条扩展类型。

##### 记录组件

| 组件 | 类型 | 说明 |
|---|---|---|
| `name` | `String` |  |
| `base` | `String` |  |
| `kind` | `Types.Kind` |  |
| `basePattern` | `Pattern` |  |
| `pattern` | `Pattern` |  |

##### 方法

```java
public boolean isBase()
```

是否为基础类型（没有额外的扩展正则）。

### `top.x0a.strapper.ValueFormat`

```java
public record ValueFormat(String source, Requirement requirement, String typeName, ValueFormat.Range range, Integer length, String defaultValue, String comment)
```

一条值的格式描述，对应 valueFormat.md 的语法：

```java
"[*|!]<格式名>[数值范围][:长度][#默认值]//说明"
```

例如 `"*string{男,女}:4#男//性别"`、`"*int[1990,2030]:4//年份"`、
`"mobile//手机号"`。

#### 记录组件

| 组件 | 类型 | 说明 |
|---|---|---|
| `source` | `String` | 原始格式串 |
| `requirement` | `Requirement` | 必填性 |
| `typeName` | `String` | 格式名，可以是短名（`mobile`）或全名（`string.mobile`） |
| `range` | `ValueFormat.Range` | 取值范围，没写时为 `null` |
| `length` | `Integer` | 原始值允许的最大字符长度，没写时为 `null` |
| `defaultValue` | `String` | 取值失败时兜底用的默认值，没写时为 `null` |
| `comment` | `String` | 给产品、业务同学看的说明 |

#### 方法

```java
public boolean hasDefault()
```

是否写了默认值（`#` 后面即使是空串也算写了）。

```java
public static ValueFormat parse(String source)
```

解析一条值格式描述；语法错误时抛 `FormatException`。

#### `top.x0a.strapper.ValueFormat.Range`

```java
public static record Range(boolean enumeration, List<String> members, String min, String max, boolean minInclusive, boolean maxInclusive, String source)
```

取值范围。两种形态：

- 枚举 `{男,女}` —— `enumeration()` 为 true，成员在 `members()`
- 区间 `(1,100]` —— 端点在 `min()`/`max()`，
  开闭由 `minInclusive()`/`maxInclusive()` 决定；端点写空表示该侧无界

##### 记录组件

| 组件 | 类型 | 说明 |
|---|---|---|
| `enumeration` | `boolean` |  |
| `members` | `List<String>` |  |
| `min` | `String` |  |
| `max` | `String` |  |
| `minInclusive` | `boolean` |  |
| `maxInclusive` | `boolean` |  |
| `source` | `String` |  |

##### 方法

```java
public boolean lowerUnbounded()
```

该侧是否无界。

```java
public boolean upperUnbounded()
```
