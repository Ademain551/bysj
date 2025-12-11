"""
生成管理员密码的 BCrypt 哈希值
用于创建系统管理员账户
"""
import bcrypt

# 管理员密码
password = "88888888"

# 生成哈希
password_hash = bcrypt.hashpw(password.encode('utf-8'), bcrypt.gensalt())
print(f"密码: {password}")
print(f"BCrypt 哈希: {password_hash.decode('utf-8')}")

# 验证
if bcrypt.checkpw(password.encode('utf-8'), password_hash):
    print("✓ 密码验证成功")
else:
    print("✗ 密码验证失败")

