# M02 基础数据模块 — 设计文档

- 日期：2026-08-19
- 状态：Approved
- 实现方案：方案 B（精简 Service 接口 + 自定义 Mapper 方法）

## 1. 需求概述

M02 基础数据模块维护资产主表（M03）的所有外键依赖表，包括：
- company（公司主体）
- asset_category（资产分类，二级树）
- asset_location（区域位置，多级树 + materialized path）
- manufacturer（厂商）
- supplier（供应商）
- depreciation_rule（折旧规则，仅骨架）
- asset_model（型号，关联 category/manufacturer/depreciation）

**完成标准**：
```bash
mvn clean verify                                # 编译 + 测试通过
POST /api/v1/locations body={name:"测试位置"}    # 201 Created
GET  /api/v1/locations                          # 返回扁平列表（含 parent_id）
GET  /api/v1/models?categoryId=1                # 返回该分类下的型号列表
```

## 2. 关键决策记录

### D1. Service 接口模式：精简接口（不继承 IService）

**决策**：不使用 MyBatis Plus 的 `IService<T>`，自定义精简接口（5-8 个业务方法）。

**理由**：
- IService 提供 100+ 方法，对于基础数据 CRUD 是过度设计
- 精简接口更清晰，只暴露业务需要的方法
- 符合 ENGINEERING.md P2（高内聚低耦合）
- 为 M03-M09 业务模块树立良好范例

**示例**：
```java
public interface CategoryService {
    List<Category> list();
    Category getById(Long id);
    void save(Category category);
    void updateById(Category category);
    void deleteById(Long id);
}
```

### D2. 树形结构返回方式：扁平列表（前端自组装）

**决策**：`asset_category` 和 `asset_location` 的 GET 接口返回扁平列表（包含 `parent_id`），由前端自行组装成树。

**理由**：
- 后端实现简单（直接返回数据库查询结果）
- 数据传输体积最小
- 本项目数据量小（分类/位置预计几十到几百条），前端组装性能无瓶颈
- 保持后端简洁，M02 快速完成为 M03 铺路

**备选方案**：未来如需树形接口，可无缝添加 `GET /categories/tree` 端点。

### D3. 种子数据范围：简化集（核心数据 + 示例）

**决策**：种子数据脚本插入：
- company：1 条（森科五金，code=SK）
- asset_category：4 个核心分类（镀膜/辅助/检测/IT设备）
- asset_location：5 个叶子节点（物料仓/设备仓/IT在用仓/IT闲置仓/设备维护仓）
- 其他表留空

**理由**：
- M02 重点是验证 CRUD 功能，不是完整数据迁移
- 完整历史数据迁移由 M08 负责
- 核心数据足够支持 M03 开发测试

### D4. depreciation_rule 处理：仅创建骨架

**决策**：创建 Entity + Mapper + 空 Service 接口，不实现 Controller 和完整 CRUD。

**理由**：
- M02 文档明确"CRUD 实现低优先级"
- 折旧逻辑属于 Phase 4（M09），当前不需要完整功能
- 骨架代码预留结构，M09 实现时补全

### D5. 权限控制：延迟到后续版本

**决策**：M02 所有接口仅做 token 验证（TokenAuthFilter），暂不实现细粒度权限控制。

**理由**：
- 当前阶段主要是开发团队内部使用
- comm_public_basic 的权限配置可能尚未完成
- 保持 M02 实现简洁，权限体系后续统一添加（如 @PreAuthorize 注解）

## 3. 架构设计

### 3.1 包结构（按上下文分包）

```
com.sk.asset/
├── entity/basedata/
│   ├── Company.java
│   ├── Category.java
│   ├── Location.java
│   ├── Manufacturer.java
│   ├── Supplier.java
│   ├── DepreciationRule.java
│   └── AssetModel.java
├── mapper/basedata/
│   ├── CompanyMapper.java
│   ├── CategoryMapper.java
│   ├── LocationMapper.java         # 含自定义 selectByParentPath
│   ├── ManufacturerMapper.java
│   ├── SupplierMapper.java
│   ├── DepreciationRuleMapper.java
│   └── AssetModelMapper.java       # 含自定义 JOIN 查询
├── service/basedata/
│   ├── CompanyService.java + Impl
│   ├── CategoryService.java + Impl
│   ├── LocationService.java + Impl
│   ├── ManufacturerService.java + Impl
│   ├── SupplierService.java + Impl
│   ├── DepreciationRuleService.java (空接口)
│   └── AssetModelService.java + Impl
├── controller/basedata/
│   ├── CompanyController.java      # 只读
│   ├── CategoryController.java
│   ├── LocationController.java
│   ├── ManufacturerController.java
│   ├── SupplierController.java
│   └── AssetModelController.java
└── dto/basedata/
    ├── category/  (CategoryReq, CategoryResp)
    ├── location/  (LocationReq, LocationResp)
    ├── manufacturer/
    ├── supplier/
    └── model/
```

### 3.2 依赖关系

```
Controller → Service → Mapper → Entity
     ↓           ↓
    DTO      (转换逻辑)
```

**红线遵守**：
- controller 不直调 mapper（R1）
- entity 不外泄到 controller，必须经 DTO 转换（R1）
- 上下文内高内聚（P2）

### 3.3 Entity 设计要点

**公共字段**（所有表）：
```java
@TableLogic
private Integer deleted;  // 0-正常 1-已删除

@TableField(fill = FieldFill.INSERT)
private LocalDateTime createdAt;

@TableField(fill = FieldFill.INSERT_UPDATE)
private LocalDateTime updatedAt;
```

**树形字段**：
- `Category`: `Long parentId`（可空，NULL=顶级）
- `Location`: `Long parentId` + `String path`（materialized path，格式 `/1/5/12/`）

**关联字段**：
- `AssetModel`: `categoryId`, `manufacturerId`, `depreciationId`（可空）

## 4. API 设计

### 4.1 标准 CRUD 端点

**manufacturer / supplier（完全相同）**：
```
GET    /api/v1/manufacturers           # 列表查询
GET    /api/v1/manufacturers/{id}      # 详情查询
POST   /api/v1/manufacturers           # 新增
PUT    /api/v1/manufacturers/{id}      # 更新
DELETE /api/v1/manufacturers/{id}      # 逻辑删除
```

### 4.2 特殊端点

**company（只读）**：
```
GET /api/v1/companies                  # 列表查询（暂不支持新增/修改/删除）
```

**category（扁平树）**：
```
GET /api/v1/categories                 # 返回扁平列表（含 parent_id，前端自组装）
POST /api/v1/categories
PUT /api/v1/categories/{id}
DELETE /api/v1/categories/{id}
```

**location（扁平树 + 子树查询）**：
```
GET /api/v1/locations                  # 返回扁平列表（含 parent_id + path）
GET /api/v1/locations?parentId={id}    # 查询指定父节点的所有子节点
POST /api/v1/locations
PUT /api/v1/locations/{id}
DELETE /api/v1/locations/{id}
```

**asset_model（关联查询）**：
```
GET /api/v1/models                     # 列表查询
GET /api/v1/models?categoryId={id}     # 按分类筛选
GET /api/v1/models/{id}
POST /api/v1/models
PUT /api/v1/models/{id}
DELETE /api/v1/models/{id}
```

### 4.3 权限控制

- 所有接口使用 `@SecurityRequirement(name = "BearerAuth")`（Swagger 标记）
- TokenAuthFilter 自动校验 token
- Controller 无需额外权限检查（细粒度权限延迟到后续版本）

### 4.4 响应格式

统一使用 `Result<T>` 包装：
```json
{
  "code": 200,
  "message": "success",
  "data": { ... }
}
```

## 5. 核心实现细节

### 5.1 LocationMapper 自定义查询（materialized path）

```java
public interface LocationMapper extends BaseMapper<Location> {
    /**
     * 查询指定路径下的所有子节点（含自身）
     * @param parentPath 父节点路径（如 "/1/" 或 "/1/5/"）
     * @return 子树节点列表（扁平）
     */
    @Select("SELECT * FROM asset_location " +
            "WHERE deleted = 0 AND path LIKE CONCAT(#{parentPath}, '%') " +
            "ORDER BY path, sort_order")
    List<Location> selectByParentPath(@Param("parentPath") String parentPath);
}
```

**使用场景**：`GET /locations?parentId=5` → 查询 id=5 的 path，再调用此方法。

### 5.2 AssetModelMapper 关联查询

```java
public interface AssetModelMapper extends BaseMapper<AssetModel> {
    /**
     * 按分类查询型号，JOIN 返回关联对象名称
     */
    @Select("SELECT m.*, " +
            "c.name as category_name, " +
            "mf.name as manufacturer_name, " +
            "dr.name as depreciation_rule_name " +
            "FROM asset_model m " +
            "LEFT JOIN asset_category c ON m.category_id = c.id " +
            "LEFT JOIN manufacturer mf ON m.manufacturer_id = mf.id " +
            "LEFT JOIN depreciation_rule dr ON m.depreciation_id = dr.id " +
            "WHERE m.deleted = 0 AND m.category_id = #{categoryId}")
    @Results({
        @Result(property = "categoryName", column = "category_name"),
        @Result(property = "manufacturerName", column = "manufacturer_name"),
        @Result(property = "depreciationRuleName", column = "depreciation_rule_name")
    })
    List<AssetModelWithRelations> selectByCategoryIdWithRelations(@Param("categoryId") Long categoryId);
}
```

**DTO 设计**：`AssetModelResp` 包含 `categoryName`, `manufacturerName` 等字段（避免前端二次查询）。

### 5.3 DTO 转换模式（手写静态工厂）

**Request DTO**（用于 POST/PUT）：
```java
public class ManufacturerReq {
    @NotBlank(message = "厂商名称不能为空")
    @Size(max = 200, message = "厂商名称长度不能超过 200")
    private String name;
    
    @Size(max = 100, message = "联系人长度不能超过 100")
    private String contact;
    
    // ... 其他字段
    
    public Manufacturer toEntity() {
        Manufacturer entity = new Manufacturer();
        entity.setName(this.name);
        entity.setContact(this.contact);
        // ...
        return entity;
    }
}
```

**Response DTO**（用于 GET）：
```java
public class ManufacturerResp {
    private Long id;
    private String name;
    private String contact;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    public static ManufacturerResp from(Manufacturer entity) {
        ManufacturerResp resp = new ManufacturerResp();
        resp.setId(entity.getId());
        resp.setName(entity.getName());
        resp.setContact(entity.getContact());
        resp.setCreatedAt(entity.getCreatedAt());
        resp.setUpdatedAt(entity.getUpdatedAt());
        return resp;
    }
}
```

### 5.4 Controller 标准写法

```java
@Tag(name = "厂商管理")
@RestController
@RequestMapping("/api/v1/manufacturers")
public class ManufacturerController {
    
    @Autowired
    private ManufacturerService manufacturerService;
    
    @Operation(summary = "厂商列表")
    @SecurityRequirement(name = "BearerAuth")
    @GetMapping
    public Result<List<ManufacturerResp>> list() {
        List<Manufacturer> list = manufacturerService.list();
        return Result.ok(list.stream()
            .map(ManufacturerResp::from)
            .collect(Collectors.toList()));
    }
    
    @Operation(summary = "新增厂商")
    @SecurityRequirement(name = "BearerAuth")
    @PostMapping
    public Result<ManufacturerResp> create(@RequestBody @Valid ManufacturerReq req) {
        Manufacturer entity = req.toEntity();
        manufacturerService.save(entity);
        return Result.ok(ManufacturerResp.from(entity));
    }
    
    // ... 其他方法
}
```

## 6. 种子数据脚本

**文件名**：`V20260820__seed_base_data.sql`（晚于 V20260819，避免 Flyway 撞号）

**插入内容**：

```sql
-- 1. 公司主体（1 条）
INSERT INTO company (code, name, remark) VALUES
('SK', '森科五金(深圳)有限公司', '主体公司');

-- 2. 资产分类（4 个核心分类，扁平结构，不建二级层级）
INSERT INTO asset_category (name, code, sort_order) VALUES
('镀膜设备', 'COATING', 10),
('辅助设备', 'AUXILIARY', 20),
('检测设备', 'TESTING', 30),
('IT设备、数码产品', 'IT_DIGITAL', 40);

-- 3. 区域位置（5 个叶子节点，简化层级）
-- path 字段暂时简化为 /id/，后续可根据实际层级调整
INSERT INTO asset_location (name, code, path, sort_order) VALUES
('森科物料仓', 'MATERIAL_WH', '/1/', 10),
('森科设备仓', 'EQUIPMENT_WH', '/2/', 20),
('IT部在用仓', 'IT_INUSE_WH', '/3/', 30),
('IT部闲置仓', 'IT_IDLE_WH', '/4/', 40),
('设备维护仓', 'MAINTENANCE_WH', '/5/', 50);
```

**注意事项**：
- path 字段在实际使用时需要根据父子关系构建（如 `/1/3/` 表示 3 是 1 的子节点）
- 当前简化版本每个节点都是顶级节点（`parent_id=NULL`，`path=/id/`）
- M08 迁移时会解析完整的层级路径（如"森科/总办/IT部/IT部在用仓"）

## 7. 测试策略

### 7.1 单元测试覆盖

**Service 层测试**（6 个 Service × 5 个核心方法 = 30 个测试）：
```java
@ExtendWith(MockitoExtension.class)
class ManufacturerServiceImplTest {
    @Mock
    private ManufacturerMapper manufacturerMapper;
    
    @InjectMocks
    private ManufacturerServiceImpl manufacturerService;
    
    @Test
    void list_shouldReturnAllManufacturers() {
        // Mock mapper.selectList 返回数据
        // 验证 service.list() 调用正确
    }
    
    @Test
    void save_shouldCallMapperInsert() {
        // 验证 service.save() 调用 mapper.insert()
    }
    
    // ... 其他测试
}
```

**Controller 层测试**（集成测试，使用 MockMvc）：
```java
@WebMvcTest(ManufacturerController.class)
class ManufacturerControllerTest {
    @Autowired
    private MockMvc mockMvc;
    
    @MockBean
    private ManufacturerService manufacturerService;
    
    @MockBean
    private TokenAuthFilter tokenAuthFilter; // Mock 鉴权
    
    @Test
    void list_shouldReturn200() throws Exception {
        // Mock service.list() 返回数据
        // 执行 GET /api/v1/manufacturers
        // 验证状态码 200、DTO 结构正确
    }
    
    @Test
    void create_shouldReturn200AndCallService() throws Exception {
        // 执行 POST /api/v1/manufacturers
        // 验证 service.save() 被调用
    }
}
```

**Mapper 层测试**（可选，使用 MyBatis Test）：
- 重点测试自定义 SQL（LocationMapper.selectByParentPath, AssetModelMapper JOIN）
- 如果时间紧张，可跳过（通过 Service 测试间接覆盖）

### 7.2 完成标准验证

```bash
# 1. 编译 + 测试通过
mvn clean verify

# 2. 启动应用，访问 Swagger 文档
open http://localhost:6006/doc.html

# 3. 手动测试核心接口
POST /api/v1/locations
Body: {"name": "测试位置", "parentId": null}
Expected: 201 Created

GET /api/v1/locations
Expected: 200 OK，返回扁平列表（含 parent_id + path）

GET /api/v1/models?categoryId=1
Expected: 200 OK，返回型号列表（初始为空数组）
```

## 8. 实现顺序（分步交付）

建议按依赖关系分 6 步实现（每步可独立测试）：

### Step 1: Entity + Mapper（1-2 小时）
- 创建 7 个 Entity（含 MyBatis Plus 注解）
- 创建 7 个 Mapper 接口（继承 BaseMapper）
- LocationMapper 增加 selectByParentPath 方法
- AssetModelMapper 增加 JOIN 查询方法
- **验证**：Spring 启动成功，MyBatis 扫描到 Mapper

### Step 2: 简单 CRUD - company/manufacturer/supplier（2 小时）
- Service 接口 + Impl（3 × 2 = 6 个文件）
- Controller + DTO（3 × 3 = 9 个文件）
- 单测（Service 3 × 5 = 15 个，Controller 3 × 3 = 9 个）
- **验证**：Swagger 显示端点，Postman 测试 CRUD 通过

### Step 3: 树形结构 - category/location（2-3 小时）
- CategoryService + Impl + Controller + DTO
- LocationService（含 listChildren 方法）+ Impl + Controller + DTO
- 单测（重点测试树形查询逻辑）
- **验证**：GET /locations?parentId=1 返回子节点

### Step 4: 关联查询 - asset_model（2 小时）
- AssetModelService + Impl + Controller + DTO
- DTO 包含关联对象名称（categoryName, manufacturerName）
- 单测（含 ?categoryId 筛选）
- **验证**：GET /models?categoryId=1 返回关联数据

### Step 5: depreciation_rule 骨架（30 分钟）
- Entity + Mapper
- DepreciationRuleService 空接口
- **不创建** Controller
- **验证**：Spring 启动成功

### Step 6: 种子数据脚本（1 小时）
- 编写 V20260820__seed_base_data.sql
- 本地 Flyway 验证（清空库重新迁移）
- **验证**：
  - GET /companies 返回 1 条（SK）
  - GET /categories 返回 4 条
  - GET /locations 返回 5 条

**总预估时间**：8-10 小时

## 9. 风险与注意事项

### 9.1 已知风险

1. **Flyway 版本号冲突**：
   - 问题：M02 文档中的 `V20260819` 已被 M01-A 占用
   - 解决：使用 `V20260820` 或更晚的版本号

2. **树形查询性能**：
   - 问题：materialized path 查询在数据量超过 1000 条时可能变慢
   - 缓解：path 字段已建索引（V20260819 脚本中）
   - 监控：如未来性能问题，考虑改用 Nested Set 或分页加载

3. **前端组装复杂度**：
   - 问题：扁平列表转树形需要前端实现递归逻辑
   - 缓解：提供前端示例代码（约 20 行）
   - 备选：未来可无缝添加 GET /categories/tree 端点

4. **depreciation_rule 临时性**：
   - 问题：骨架代码可能在 M09 实现时需要重构
   - 接受：当前阶段不需要完整功能，临时方案可接受

### 9.2 工程红线检查

- ✅ **R1（分层依赖）**：controller → service → mapper → entity
- ✅ **R2（编译测试）**：每步完成后执行 mvn clean test
- ✅ **R3（配置隔离）**：无新增配置项
- ✅ **R4（提交纪律）**：按功能点提交（6 个独立 commit）
- ✅ **R5（License）**：无新增依赖
- ✅ **R6（数据库）**：只操作本地/测试库
- ✅ **R7（上下文传承）**：完成后更新 STATUS.md
- ✅ **R8（鉴权）**：复用 TokenAuthFilter，不自建权限

## 10. 后续扩展预留

### 10.1 细粒度权限控制（Phase 3+）

当 comm_public_basic 权限配置就绪后，可在 Controller 方法上添加权限注解：

```java
@PreAuthorize("hasAuthority('asset:manufacturer:create')")
@PostMapping
public Result<ManufacturerResp> create(@RequestBody @Valid ManufacturerReq req) {
    // ...
}
```

### 10.2 树形接口（如需要）

添加树形端点（向后兼容）：

```java
@GetMapping("/tree")
public Result<List<CategoryTreeResp>> tree() {
    // 递归组装树形结构
}
```

### 10.3 分页查询（数据量增大后）

当列表数据超过 100 条时，考虑添加分页：

```java
@GetMapping
public Result<Page<ManufacturerResp>> list(
    @RequestParam(defaultValue = "1") int page,
    @RequestParam(defaultValue = "20") int size
) {
    // 使用 MyBatis Plus 分页插件
}
```

### 10.4 多公司管理（未来版本）

当需要支持多公司时，开放 company 的 POST/PUT/DELETE 接口，并在其他表增加 company_id 筛选。

---

## 附录：关键类签名

### A1. Service 接口示例

```java
public interface ManufacturerService {
    List<Manufacturer> list();
    Manufacturer getById(Long id);
    void save(Manufacturer manufacturer);
    void updateById(Manufacturer manufacturer);
    void deleteById(Long id);
}

public interface LocationService {
    List<Location> list();
    List<Location> listChildren(Long parentId);  // 特殊方法
    Location getById(Long id);
    void save(Location location);
    void updateById(Location location);
    void deleteById(Long id);
}

public interface AssetModelService {
    List<AssetModel> list();
    List<AssetModel> listByCategoryId(Long categoryId);  // 特殊方法
    AssetModel getById(Long id);
    void save(AssetModel model);
    void updateById(AssetModel model);
    void deleteById(Long id);
}
```

### A2. DTO 示例

```java
// Request DTO
public class LocationReq {
    @NotBlank
    @Size(max = 200)
    private String name;
    
    @Size(max = 50)
    private String code;
    
    private Long parentId;  // 可空
    
    @Min(0)
    private Integer sortOrder;
    
    @Size(max = 500)
    private String remark;
    
    public Location toEntity() { /* ... */ }
}

// Response DTO
public class LocationResp {
    private Long id;
    private String name;
    private String code;
    private Long parentId;
    private String path;
    private Integer sortOrder;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    public static LocationResp from(Location entity) { /* ... */ }
}
```

---

**设计文档结束。下一步：调用 writing-plans skill 创建实现计划。**
