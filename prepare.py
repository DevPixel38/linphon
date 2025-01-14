#!/usr/bin/env python

############################################################################
# prepare.py
# Copyright (C) 2016  Belledonne Communications, Grenoble France
#
############################################################################
#
# This program is free software; you can redistribute it and/or
# modify it under the terms of the GNU General Public License
# as published by the Free Software Foundation; either version 2
# of the License, or (at your option) any later version.
#
# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# GNU General Public License for more details.
#
# You should have received a copy of the GNU General Public License
# along with this program; if not, write to the Free Software
# Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
#
############################################################################

import fnmatch
import os
import re
import sys
from distutils.spawn import find_executable
from logging import error, warning, info
from subprocess import Popen
sys.dont_write_bytecode = True
sys.path.insert(0, 'submodules/cmake-builder')
try:
    import prepare
except Exception as e:
    error(
        "Could not find prepare module: {}, probably missing submodules/cmake-builder? Try running:\n"
        "git submodule sync && git submodule update --init --recursive".format(e))
    exit(1)



class AndroidTarget(prepare.Target):

    def __init__(self, arch):
        prepare.Target.__init__(self, 'android-' + arch)
        current_path = os.path.dirname(os.path.realpath(__file__))
        self.config_file = 'configs/config-android.cmake'
        self.toolchain_file = 'toolchains/toolchain-android-' + arch + '.cmake'
        self.output = 'liblinphone-sdk/android-' + arch
        self.external_source_path = os.path.join(current_path, 'submodules')


class AndroidArmTarget(AndroidTarget):

    def __init__(self):
        AndroidTarget.__init__(self, 'arm')
        self.additional_args += ['-DENABLE_VIDEO=NO']


class AndroidArmv7Target(AndroidTarget):

    def __init__(self):
        AndroidTarget.__init__(self, 'armv7')


class AndroidArm64Target(AndroidTarget):

    def __init__(self):
        AndroidTarget.__init__(self, 'arm64')


class AndroidX86Target(AndroidTarget):

    def __init__(self):
        AndroidTarget.__init__(self, 'x86')




android_targets = {
    'arm': AndroidArmTarget(),
    'armv7': AndroidArmv7Target(),
    'arm64': AndroidArm64Target(),
    'x86': AndroidX86Target()
}

class AndroidPreparator(prepare.Preparator):

    def __init__(self, targets=android_targets):
        prepare.Preparator.__init__(self, targets, default_targets=['armv7', 'arm64', 'x86'])
        self.min_supported_ndk = 10
        self.max_supported_ndk = 13
        self.unsupported_ndk_version = None
        self.min_cmake_version = "3.0"
        self.release_with_debug_info = True
        self.veryclean = True
        self.show_gpl_disclaimer = True
        self.argparser.add_argument('-ac', '--all-codecs', help="Enable all codecs, including the non-free ones", action='store_true')

    def parse_args(self):
        prepare.Preparator.parse_args(self)

        if self.args.all_codecs:
            self.additional_args += ["-DENABLE_GPL_THIRD_PARTIES=YES"]
            self.additional_args += ["-DENABLE_NON_FREE_CODECS=YES"]
            self.additional_args += ["-DENABLE_AMRNB=YES"]
            self.additional_args += ["-DENABLE_AMRWB=YES"]
            self.additional_args += ["-DENABLE_BV16=YES"]
            self.additional_args += ["-DENABLE_CODEC2=YES"]
            self.additional_args += ["-DENABLE_G729=YES"]
            self.additional_args += ["-DENABLE_GSM=YES"]
            self.additional_args += ["-DENABLE_ILBC=YES"]
            self.additional_args += ["-DENABLE_ISAC=YES"]
            self.additional_args += ["-DENABLE_OPUS=YES"]
            self.additional_args += ["-DENABLE_SILK=YES"]
            self.additional_args += ["-DENABLE_SPEEX=YES"]
            self.additional_args += ["-DENABLE_FFMPEG=YES"]
            self.additional_args += ["-DENABLE_H263=YES"]
            self.additional_args += ["-DENABLE_H263P=YES"]
            self.additional_args += ["-DENABLE_MPEG4=YES"]
            self.additional_args += ["-DENABLE_OPENH264=YES"]
            self.additional_args += ["-DENABLE_VPX=YES"]
            # self.additional_args += ["-DENABLE_X264=YES"] # Do not activate x264 because it has text relocation issues

    def list_feature_target(self):
        return android_targets['armv7']

    def check_ndk_version(self):
        retval = True
        ndk_build = find_executable('ndk-build')
        ndk_path = os.path.dirname(ndk_build)
        print ndk_path
        ndk_path = ndk_path.replace("/build", "")
        # NDK prior to r11 had a RELEASE.TXT file holding the version number
        release_file = os.path.join(ndk_path, 'RELEASE.TXT')
        if os.path.isfile(release_file):
            version = open(release_file).read().strip()
            res = re.match('^r(\d+)(.*)$', version)
            version = int(res.group(1))
            retval = False
        else:
            # Hack to find the NDK version since the RELEASE.TXT file is no longer there
            python_config_files = []
            for root, dirnames, filenames in os.walk(ndk_path):
                for filename in fnmatch.filter(filenames, 'python-config'):
                    python_config_files.append(os.path.join(root, filename))
            if len(python_config_files) > 0:
                version = open(python_config_files[0]).readlines()[0]
                res = re.match('^.*/(aosp-)?ndk-r(\d+).*$', version)
                version = int(res.group(2))
                retval = False
            else:
                error("Could not get Android NDK version!")
                sys.exit(-1)
        if retval == False and (version < self.min_supported_ndk or version > self.max_supported_ndk):
            self.unsupported_ndk_version = version
            retval = True
        return retval
        
    def check_environment(self):
        ret = 0
        ret_sdk = not self.check_is_installed('android', 'Android SDK tools')
        ret_ndk = not self.check_is_installed('ndk-build', 'Android NDK r{}'.format(self.max_supported_ndk))
        
        if not ret_ndk:
            ret_ndk = self.check_ndk_version()
        ret |= ret_sdk
        ret |= ret_ndk
        ret |= prepare.Preparator.check_environment(self)
        return ret

    def show_environment_errors(self):
        if self.unsupported_ndk_version is not None:
            error("Unsupported Android NDK r{}. Please install version r{}.".format(self.unsupported_ndk_version, self.max_supported_ndk))
        else:
            prepare.Preparator.show_environment_errors(self)

    def clean(self):
        prepare.Preparator.clean(self)
        if os.path.isfile('Makefile'):
            os.remove('Makefile')
        if os.path.isdir('WORK') and not os.listdir('WORK'):
            os.rmdir('WORK')
        if os.path.isdir('liblinphone-sdk') and not os.listdir('liblinphone-sdk'):
            os.rmdir('liblinphone-sdk')

    def generate_makefile(self, generator, project_file=''):
        platforms = self.args.target
        arch_targets = ""
        for arch in platforms:
            arch_targets += """
{arch}: {arch}-build

{arch}-build:
\t{generator} WORK/android-{arch}/cmake
\t@echo "Done"
""".format(arch=arch, generator=generator)
        makefile = """
archs={archs}
TOPDIR=$(shell pwd)
LINPHONE_ANDROID_VERSION=$(shell git describe --always)
ANDROID_MOST_RECENT_TARGET=$(shell android list target -c | grep -E 'android-[0-9]+' | tail -n1)
ANT_SILENT=$(shell ant -h | grep -q -- -S && echo 1 || echo 0)
PACKAGE_NAME=$(shell sed -nE 's|<property name="linphone.package.name" value="(.*)" />|\\1|p' custom_rules.xml)

.PHONY: all
.NOTPARALLEL: all generate-apk generate-mediastreamer2-apk install release

all: update-project generate-apk

build: $(addsuffix -build, $(archs))

clean: java-clean

install: install-apk run-linphone

java-clean:
\tant clean

$(TOPDIR)/res/raw/rootca.pem:
\tcp liblinphone-sdk/android-{first_arch}/share/linphone/rootca.pem $@

copy-libs:
\trm -rf libs-debug/armeabi
\trm -rf libs/armeabi
\tif test -d "liblinphone-sdk/android-arm"; then \\
\t\tmkdir -p libs-debug/armeabi && \\
\t\tcp -f liblinphone-sdk/android-arm/lib/libgnustl_shared.so libs-debug/armeabi && \\
\t\tcp -f liblinphone-sdk/android-arm/lib/lib*-armeabi.so libs-debug/armeabi && \\
\t\tcp -f liblinphone-sdk/android-arm/lib/mediastreamer/plugins/*.so libs-debug/armeabi && \\
\t\tmkdir -p libs/armeabi && \\
\t\tcp -f liblinphone-sdk/android-arm/lib/libgnustl_shared.so libs/armeabi && \\
\t\tcp -f liblinphone-sdk/android-arm/lib/lib*-armeabi.so libs/armeabi && \\
\t\tcp -f liblinphone-sdk/android-arm/lib/mediastreamer/plugins/*.so libs/armeabi && \\
\t\tsh WORK/android-arm/strip.sh libs/armeabi/*.so; \\
\tfi
\tif test -f "liblinphone-sdk/android-arm/bin/gdbserver"; then \\
\t\tcp -f liblinphone-sdk/android-arm/bin/gdbserver libs-debug/armeabi && \\
\t\tcp -f liblinphone-sdk/android-arm/bin/gdb.setup libs-debug/armeabi && \\
\t\tcp -f liblinphone-sdk/android-arm/bin/gdbserver libs/armeabi && \\
\t\tcp -f liblinphone-sdk/android-arm/bin/gdb.setup libs/armeabi; \\
\tfi
\trm -rf libs-debug/armeabi-v7a
\trm -rf libs/armeabi-v7a
\tif test -d "liblinphone-sdk/android-armv7"; then \\
\t\tmkdir -p libs-debug/armeabi-v7a && \\
\t\tcp -f liblinphone-sdk/android-armv7/lib/libgnustl_shared.so libs-debug/armeabi-v7a && \\
\t\tcp -f liblinphone-sdk/android-armv7/lib/lib*-armeabi-v7a.so libs-debug/armeabi-v7a && \\
\t\tcp -f liblinphone-sdk/android-armv7/lib/mediastreamer/plugins/*.so libs-debug/armeabi-v7a && \\
\t\tmkdir -p libs/armeabi-v7a && \\
\t\tcp -f liblinphone-sdk/android-armv7/lib/libgnustl_shared.so libs/armeabi-v7a && \\
\t\tcp -f liblinphone-sdk/android-armv7/lib/lib*-armeabi-v7a.so libs/armeabi-v7a && \\
\t\tcp -f liblinphone-sdk/android-armv7/lib/mediastreamer/plugins/*.so libs/armeabi-v7a && \\
\t\tsh WORK/android-armv7/strip.sh libs/armeabi-v7a/*.so; \\
\tfi
\tif test -f "liblinphone-sdk/android-armv7/bin/gdbserver"; then \\
\t\tcp -f liblinphone-sdk/android-armv7/bin/gdbserver libs-debug/armeabi-v7a && \\
\t\tcp -f liblinphone-sdk/android-armv7/bin/gdb.setup libs-debug/armeabi-v7a && \\
\t\tcp -f liblinphone-sdk/android-armv7/bin/gdbserver libs/armeabi-v7a && \\
\t\tcp -f liblinphone-sdk/android-armv7/bin/gdb.setup libs/armeabi-v7a; \\
\tfi
\trm -rf libs-debug/arm64-v8a
\trm -rf libs/arm64-v8a
\tif test -d "liblinphone-sdk/android-arm64"; then \\
\t\tmkdir -p libs-debug/arm64-v8a && \\
\t\tcp -f liblinphone-sdk/android-arm64/lib/libgnustl_shared.so libs-debug/arm64-v8a && \\
\t\tcp -f liblinphone-sdk/android-arm64/lib/lib*-arm64-v8a.so libs-debug/arm64-v8a && \\
\t\tcp -f liblinphone-sdk/android-arm64/lib/mediastreamer/plugins/*.so libs-debug/arm64-v8a && \\
\t\tmkdir -p libs/arm64-v8a && \\
\t\tcp -f liblinphone-sdk/android-arm64/lib/libgnustl_shared.so libs/arm64-v8a && \\
\t\tcp -f liblinphone-sdk/android-arm64/lib/lib*-arm64-v8a.so libs/arm64-v8a && \\
\t\tcp -f liblinphone-sdk/android-arm64/lib/mediastreamer/plugins/*.so libs/arm64-v8a && \\
\t\tsh WORK/android-arm64/strip.sh libs/arm64-v8a/*.so; \\
\tfi
\tif test -f "liblinphone-sdk/android-arm64/bin/gdbserver"; then \\
\t\tcp -f liblinphone-sdk/android-arm64/bin/gdbserver libs-debug/arm64-v8a && \\
\t\tcp -f liblinphone-sdk/android-arm64/bin/gdb.setup libs-debug/arm64-v8a && \\
\t\tcp -f liblinphone-sdk/android-arm64/bin/gdbserver libs/arm64-v8a && \\
\t\tcp -f liblinphone-sdk/android-arm64/bin/gdb.setup libs/arm64-v8a; \\
\tfi
\trm -rf libs-debug/x86
\trm -rf libs/x86
\tif test -d "liblinphone-sdk/android-x86"; then \\
\t\tmkdir -p libs-debug/x86 && \\
\t\tcp -f liblinphone-sdk/android-x86/lib/libgnustl_shared.so libs-debug/x86 && \\
\t\tcp -f liblinphone-sdk/android-x86/lib/lib*-x86.so libs-debug/x86 && \\
\t\tcp -f liblinphone-sdk/android-x86/lib/mediastreamer/plugins/*.so libs-debug/x86 && \\
\t\tmkdir -p libs/x86 && \\
\t\tcp -f liblinphone-sdk/android-x86/lib/libgnustl_shared.so libs/x86 && \\
\t\tcp -f liblinphone-sdk/android-x86/lib/lib*-x86.so libs/x86 && \\
\t\tcp -f liblinphone-sdk/android-x86/lib/mediastreamer/plugins/*.so libs/x86 && \\
\t\tsh WORK/android-x86/strip.sh libs/x86/*.so; \\
\tfi
\tif test -f "liblinphone-sdk/android-x86/bin/gdbserver"; then \\
\t\tcp -f liblinphone-sdk/android-x86/bin/gdbserver libs-debug/x86 && \\
\t\tcp -f liblinphone-sdk/android-x86/bin/gdb.setup libs-debug/x86 && \\
\t\tcp -f liblinphone-sdk/android-x86/bin/gdbserver libs/x86 && \\
\t\tcp -f liblinphone-sdk/android-x86/bin/gdb.setup libs/x86; \\
\tfi

update-project:
\tandroid update project --path . --target $(ANDROID_MOST_RECENT_TARGET)
\tandroid update test-project --path tests -m .

update-mediastreamer2-project:
\t@cd $(TOPDIR)/submodules/linphone/mediastreamer2/java && \\
\tandroid update project --path . --target $(ANDROID_MOST_RECENT_TARGET)

generate-apk: java-clean build copy-libs $(TOPDIR)/res/raw/rootca.pem update-project
\techo "version.name=$(LINPHONE_ANDROID_VERSION)" > default.properties && \\
\tant debug

generate-mediastreamer2-apk: java-clean build copy-libs update-mediastreamer2-project
\t@cd $(TOPDIR)/submodules/linphone/mediastreamer2/java && \\
\techo "version.name=$(LINPHONE_ANDROID_VERSION)" > default.properties && \\
\tant debug

quick:
\tant clean
\tant debug
\tant installd
\tant run

install-apk:
\tant installd

uninstall:
\tadb uninstall $(PACKAGE_NAME)

release: java-clean build copy-libs update-project
\tpatch -p1 < release.patch
\tcat ant.properties | grep version.name > default.properties
\tant release
\tpatch -Rp1 < release.patch

generate-sdk: liblinphone-android-sdk

liblinphone-android-sdk: generate-apk
\tant liblinphone-android-sdk

linphone-android-sdk: generate-apk
\tant linphone-android-sdk

mediastreamer2-sdk: generate-mediastreamer2-apk
\t@cd $(TOPDIR)/submodules/linphone/mediastreamer2/java && \\
\tant mediastreamer2-sdk

liblinphone_tester:
\t$(MAKE) -C liblinphone_tester

run-linphone:
\tant run

run-liblinphone-tests:
\t@cd liblinphone_tester && \\
\tmake run-all-tests

run-basic-tests: update-project
\tant partial-clean
\t$(MAKE) -C tests run-basic-tests ANT_SILENT=$(ANT_SILENT)

run-all-tests: update-project
\tant partial-clean
\t$(MAKE) -C tests run-all-tests ANT_SILENT=$(ANT_SILENT)

pull-transifex:
\ttx pull -af

push-transifex:
\ttx push -s -f --no-interactive

{arch_targets}

help-prepare-options:
\t@echo "prepare.py was previously executed with the following options:"
\t@echo "   {options}"

help: help-prepare-options
\t@echo ""
\t@echo "(please read the README.md file first)"
\t@echo ""
\t@echo "Available architectures: {archs}"
\t@echo ""
\t@echo "Available targets:"
\t@echo ""
\t@echo "   * all or generate-apk: builds all architectures and creates the linphone application APK"
\t@echo "   * generate-sdk: builds all architectures and creates the liblinphone SDK"
\t@echo "   * install: install the linphone application APK (run this only after generate-apk)"
\t@echo "   * uninstall: uninstall the linphone application"
\t@echo ""
""".format(archs=' '.join(platforms), arch_opts='|'.join(platforms),
           first_arch=platforms[0], options=' '.join(sys.argv),
           arch_targets=arch_targets, generator=generator)
        f = open('Makefile', 'w')
        f.write(makefile)
        f.close()



def main():
    preparator = AndroidPreparator()
    preparator.parse_args()
    if preparator.check_environment() != 0:
        preparator.show_environment_errors()
        return 1
    return preparator.run()

if __name__ == "__main__":
    sys.exit(main())
