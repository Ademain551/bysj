"""
将 Excel 文件中的植物病害分布情况数据导入到 MySQL 数据库的 bhxx 表中
"""
import pandas as pd
import pymysql
import sys
import os

# 数据库配置
DB_CONFIG = {
    'host': 'localhost',
    'port': 3306,
    'user': 'root',
    'password': '123456',
    'database': 'bysj',
    'charset': 'utf8mb4'
}

# Excel 文件路径
EXCEL_FILE = r'E:\Users\LENOVO\Desktop\mtjbysj\云南省植物病害分布情况表_含预防方法.xlsx'

def read_excel():
    """读取 Excel 文件，返回 DataFrame"""
    try:
        # 尝试读取第一个工作表
        df = pd.read_excel(EXCEL_FILE, sheet_name=0)
        print(f"成功读取 Excel 文件，共 {len(df)} 行数据")
        print(f"列名: {list(df.columns)}")
        print("\n前5行数据预览:")
        print(df.head())
        print("\n数据信息:")
        print(df.info())
        return df
    except Exception as e:
        print(f"读取 Excel 文件失败: {e}")
        sys.exit(1)

def create_table(cursor):
    """创建 bhxx 表"""
    # 先删除已存在的表
    cursor.execute("DROP TABLE IF EXISTS `bhxx`")
    
    # 创建表的 SQL
    create_sql = """
    CREATE TABLE `bhxx` (
      `id` bigint NOT NULL AUTO_INCREMENT,
      `植物名称` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '植物名称',
      `病害名称` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '病害名称',
      `分布区域` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT '分布区域',
      `分布时间` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '分布时间（月份/季节/年份）',
      `防治方法` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT '防治方法',
      `创建时间` datetime(6) DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
      `更新时间` datetime(6) DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
      PRIMARY KEY (`id`) USING BTREE,
      KEY `idx_植物名称` (`植物名称`) USING BTREE,
      KEY `idx_病害名称` (`病害名称`) USING BTREE
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='植物病害分布情况表';
    """
    
    try:
        cursor.execute(create_sql)
        print("成功创建 bhxx 表")
        return True
    except Exception as e:
        print(f"创建表失败: {e}")
        return False

def map_columns(df):
    """映射 Excel 列名到数据库字段名"""
    # 获取原始列名
    columns = list(df.columns)
    print(f"\nExcel 文件的列名: {columns}")
    
    # 尝试自动映射常见的列名
    column_mapping = {}
    
    # 常见的中文列名映射
    common_mappings = {
        '植物名称': ['植物名称', '植物', '作物', '作物名称', '名称'],
        '病害名称': ['病害名称', '病害', '疾病', '疾病名称'],
        '分布区域': ['分布区域', '分布', '区域', '分布地区', '地区', '发生区域'],
        '分布时间': ['分布时间', '时间', '发生时间', '月份', '季节', '年份'],
        '防治方法': ['预防方法', '防治方法', '预防', '防治', '预防措施', '防治措施', '方法']
    }
    
    # 自动匹配列名
    for db_col, possible_names in common_mappings.items():
        for col in columns:
            if any(name in col for name in possible_names):
                column_mapping[db_col] = col
                break
    
    print(f"\n列名映射结果: {column_mapping}")
    
    # 如果映射不完整，让用户确认或手动映射
    if len(column_mapping) < 5:
        print("\n警告: 无法自动映射所有列，将使用原始列名")
        # 尝试使用前5列作为默认映射
        if len(columns) >= 5:
            column_mapping = {
                '植物名称': columns[0],
                '病害名称': columns[1],
                '分布区域': columns[2],
                '分布时间': columns[3],
                '防治方法': columns[4]
            }
        else:
            # 使用所有可用的列
            for i, db_col in enumerate(['植物名称', '病害名称', '分布区域', '分布时间', '防治方法']):
                if i < len(columns):
                    column_mapping[db_col] = columns[i]
    
    return column_mapping

def insert_data(cursor, df, column_mapping):
    """插入数据到数据库"""
    insert_sql = """
    INSERT INTO `bhxx` (`植物名称`, `病害名称`, `分布区域`, `分布时间`, `防治方法`)
    VALUES (%s, %s, %s, %s, %s)
    """
    
    success_count = 0
    error_count = 0
    
    for idx, row in df.iterrows():
        try:
            # 获取映射后的列值
            plant_name = str(row[column_mapping.get('植物名称', df.columns[0])]).strip() if pd.notna(row[column_mapping.get('植物名称', df.columns[0])]) else None
            disease_name = str(row[column_mapping.get('病害名称', df.columns[1])]).strip() if pd.notna(row[column_mapping.get('病害名称', df.columns[1])]) else None
            distribution = str(row[column_mapping.get('分布区域', df.columns[2])]).strip() if pd.notna(row[column_mapping.get('分布区域', df.columns[2])]) else None
            distribution_time = str(row[column_mapping.get('分布时间', df.columns[3])]).strip() if pd.notna(row[column_mapping.get('分布时间', df.columns[3])]) else None
            prevention = str(row[column_mapping.get('防治方法', df.columns[4])]).strip() if pd.notna(row[column_mapping.get('防治方法', df.columns[4])]) else None
            
            # 跳过空行
            if not plant_name or plant_name == 'nan' or plant_name == 'None':
                continue
            
            cursor.execute(insert_sql, (plant_name, disease_name, distribution, distribution_time, prevention))
            success_count += 1
        except Exception as e:
            error_count += 1
            print(f"插入第 {idx + 1} 行数据失败: {e}")
            print(f"  数据: {row.to_dict()}")
    
    print(f"\n数据插入完成: 成功 {success_count} 条, 失败 {error_count} 条")
    return success_count, error_count

def main():
    """主函数"""
    print("=" * 60)
    print("植物病害分布情况数据导入工具")
    print("=" * 60)
    
    # 1. 读取 Excel 文件
    print("\n步骤 1: 读取 Excel 文件...")
    df = read_excel()
    
    # 2. 连接数据库
    print("\n步骤 2: 连接数据库...")
    try:
        connection = pymysql.connect(**DB_CONFIG)
        cursor = connection.cursor()
        print("数据库连接成功")
    except Exception as e:
        print(f"数据库连接失败: {e}")
        sys.exit(1)
    
    try:
        # 3. 映射列名
        print("\n步骤 3: 映射列名...")
        column_mapping = map_columns(df)
        
        # 4. 创建表
        print("\n步骤 4: 创建数据库表...")
        if not create_table(cursor):
            sys.exit(1)
        
        # 5. 插入数据
        print("\n步骤 5: 插入数据...")
        success_count, error_count = insert_data(cursor, df, column_mapping)
        
        # 6. 提交事务
        connection.commit()
        print("\n数据已成功提交到数据库")
        
        # 7. 验证数据
        cursor.execute("SELECT COUNT(*) FROM `bhxx`")
        count = cursor.fetchone()[0]
        print(f"\n数据库中共有 {count} 条记录")
        
        # 显示前几条数据
        cursor.execute("SELECT * FROM `bhxx` LIMIT 5")
        results = cursor.fetchall()
        print("\n前5条数据预览:")
        for row in results:
            print(row)
            
    except Exception as e:
        connection.rollback()
        print(f"\n操作失败，已回滚: {e}")
        import traceback
        traceback.print_exc()
    finally:
        cursor.close()
        connection.close()
        print("\n数据库连接已关闭")

if __name__ == '__main__':
    main()

