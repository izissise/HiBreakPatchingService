Automatically patch system.img for use with Bigem Hibreak, adding eink features support

I use `mingc/android-build-box` to compile the project:

```bash
mkdir -p build_cache/{jenv,gradle,android-sdk}
docker volume create --driver local --opt type=none --opt device=$PWD/build_cache/jenv/ --opt o=bind jenv-cache
docker volume create --driver local --opt type=none --opt device=$PWD/build_cache/android-sdk/ --opt o=bind android-sdk
docker run --user="$(id -u):$(id -g)" --userns=keep-id --cap-drop=ALL -v android-sdk:/opt/android-sdk/ -v jenv-cache:/root/.jenv/ -v $PWD/build_cache/gradle/:/root/.gradle/ -v $PWD:/work:rw -w /work -ti mingc/android-build-box bash
./gradlew build
```

## Licensing

### MIT License
Most of the project is licensed under the MIT License unless specified otherwise

The code and releases are provided “as is,” without any express or implied warranty of any kind including warranties of merchantability, non-infringement, title, or fitness for a particular purpose.

### Apache License 2.0
The file `HardwareGestureDetector.kt` includes code derived from AOSP. The original code is subject to the following license:

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

        http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.

Modifications and additions to the original code are licensed under the MIT License
