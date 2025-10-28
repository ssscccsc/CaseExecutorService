#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
测试用例参数解析功能
验证每个用例能够正确接收自己的参数
"""

import argparse
import json
import sys
import os

def parse_arguments():
    """解析命令行参数"""
    parser = argparse.ArgumentParser(description='测试用例参数解析功能')
    
    # 基本参数
    parser.add_argument('--ip', required=True, help='执行机IP地址')
    parser.add_argument('--category', help='业务大类')
    parser.add_argument('--app', help='APP名称')
    parser.add_argument('--dataset_round', help='数据集轮次')
    
    # 用例级别参数（这些参数会根据用例ID动态传入）
    parser.add_argument('--timeout', help='超时时间')
    parser.add_argument('--retryCount', help='重试次数')
    parser.add_argument('--quality', help='质量设置')
    
    # UE列表参数
    parser.add_argument('--uelist', help='UE设备列表JSON')
    
    return parser.parse_args()

def test_parameter_parsing():
    """测试参数解析功能"""
    args = parse_arguments()
    
    print("=== 用例参数解析测试 ===")
    print(f"执行机IP: {args.ip}")
    print(f"业务大类: {args.category}")
    print(f"APP名称: {args.app}")
    print(f"数据集轮次: {args.dataset_round}")
    
    print("\n=== 用例级别参数 ===")
    if args.timeout:
        print(f"超时时间: {args.timeout}")
    if args.retryCount:
        print(f"重试次数: {args.retryCount}")
    if args.quality:
        print(f"质量设置: {args.quality}")
    
    print("\n=== UE设备信息 ===")
    if args.uelist:
        try:
            ue_list = json.loads(args.uelist)
            print(f"UE设备数量: {len(ue_list)}")
            for i, ue in enumerate(ue_list):
                print(f"  UE{i+1}: ID={ue.get('ueId')}, 名称={ue.get('name')}, 网络类型={ue.get('networkTypeName')}")
        except json.JSONDecodeError as e:
            print(f"UE列表JSON解析失败: {e}")
    
    print("\n=== 参数验证结果 ===")
    
    # 验证用例级别参数是否正确传递
    success = True
    if not args.timeout:
        print("❌ 缺少timeout参数")
        success = False
    else:
        print(f"✅ timeout参数: {args.timeout}")
    
    if not args.retryCount:
        print("❌ 缺少retryCount参数")
        success = False
    else:
        print(f"✅ retryCount参数: {args.retryCount}")
    
    if not args.quality:
        print("❌ 缺少quality参数")
        success = False
    else:
        print(f"✅ quality参数: {args.quality}")
    
    if success:
        print("\n🎉 所有用例级别参数都正确传递！")
        return 0
    else:
        print("\n❌ 部分用例级别参数缺失！")
        return 1

if __name__ == "__main__":
    sys.exit(test_parameter_parsing())
