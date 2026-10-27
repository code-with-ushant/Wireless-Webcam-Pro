"""
Build script for the native C protocol parser extension.

Run:  python setup_native.py build_ext --inplace
"""
from setuptools import setup, Extension

setup(
    name="native_protocol",
    ext_modules=[
        Extension(
            "_native_protocol",
            sources=["protocol/_native_protocol.c"],
            language="c",
        )
    ],
)
