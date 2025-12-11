import os
import random
from typing import Any, Dict, Optional, Union

import numpy as np
import torch


def set_seed(seed: int = 42) -> None:
    """设置全局随机种子，提升实验可复现性。"""
    random.seed(seed)
    np.random.seed(seed)
    torch.manual_seed(seed)
    torch.cuda.manual_seed_all(seed)
    os.environ["PYTHONHASHSEED"] = str(seed)
    torch.backends.cudnn.deterministic = True
    torch.backends.cudnn.benchmark = False


def safe_torch_load(
    path: str,
    *,
    map_location: Optional[Union[torch.device, str]] = None,
    weights_only: bool = True,
) -> Any:
    """兼容不同PyTorch版本的 ``torch.load`` 包装。

    PyTorch 2.0 起新增 ``weights_only`` 参数，但旧版本不支持。该函数会在
    需要时自动降级以保持向后兼容，避免在不同环境运行脚本时抛出 ``TypeError``。
    """

    kwargs: Dict[str, Any] = {}
    if map_location is not None:
        kwargs["map_location"] = map_location

    if weights_only is not None:
        try:
            return torch.load(path, weights_only=weights_only, **kwargs)
        except TypeError:
            # 旧版本PyTorch不支持 weights_only，退回不带该参数的加载方式。
            return torch.load(path, **kwargs)

    # 未显式传递 weights_only 时，直接使用标准 torch.load
    return torch.load(path, **kwargs)
