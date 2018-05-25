set -eu

# List of solutions to build
CPP_SOLUTIONS=${CPP_SOLUTIONS:-"winfw"}

# Override this variable to set your own list of build configurations for
# wfpctl
CPP_BUILD_MODES=${CPP_BUILD_MODES:-"Debug Release"}
# Override this variable to set different target platforms for wfpctl
CPP_BUILD_TARGETS=${CPP_BUILD_TARGETS:-"x86 x64"}
# Override this to set a different cargo target directory
CARGO_TARGET_DIR=${CARGO_TARGET_DIR:-"./target/"}


# Builds all 4 variations of the wfpctl.dll library. Takes an argument that is
# the root of the WFP repository.
function build_wfpctl
{
  local path="$1"

  # Sometimes the build output needs to be cleaned up
  rm -r $path/bin/* || true
  
  set -x
  for mode in $CPP_BUILD_MODES; do
    for target in $CPP_BUILD_TARGETS; do
      cmd.exe "/c msbuild.exe $(to_win_path $path/winfw.sln) /p:Configuration=$mode /p:Platform=$target /t:$CPP_SOLUTIONS"
		
    done
  done
  set +x
}

function to_win_path
{
  local unixpath=$1
  # if it's a relative path and starts with a dot (.), don't transform the
  # drive prefix (/c/ -> C:\)
  if echo $unixpath | grep '^\.' >/dev/null; then
    echo $unixpath | sed -e 's/^\///' -e 's/\//\\/g' 
  # if it's an absolute path, transform the drive prefix
  else
    # remove the cygrdive prefix if it's there
    unixpath=$(echo $1 | sed -e 's/^\/cygdrive//')
    echo $unixpath | sed -e 's/^\///' -e 's/\//\\/g' -e 's/^./\0:/'
  fi
}

function copy_outputs
{
  local wfp_root_path=$1

  for mode in $CPP_BUILD_MODES; do
    for target in $CPP_BUILD_TARGETS; do
      local dll_path=$(get_wfp_output_path $wfp_root_path $target $mode)
      local cargo_target=$(get_cargo_target_dir $target $mode)
      mkdir -p $cargo_target
      cp "$dll_path/winfw.dll" $cargo_target
    done
  done

}

function get_wfp_output_path
{
  local wfp_root=$1
  local build_target=$2
  local build_mode=$3

  case $build_target in
    "x86")
      echo "$wfp_root/bin/Win32-$build_mode"
      ;;
    "x64")
      echo "$wfp_root/bin/x64-$build_mode"
      ;;
    *)
      echo Unkown build target $build_target
      exit 1
      ;;
  esac
}

# builds an appropriate cargo target path for the specified build target and
# build mode
function get_cargo_target_dir
{
  local build_target=$1
  local build_mode=$2

  local host_arch=$(rustc_host_arch)
  local host_target_arch=$(arch_from_build_target $host_arch)
  local build_target_arch=$(arch_from_build_target $build_target)
  # if the target is the same as the host, cargo omits the platform triplet
  if [ "$host_target_arch" == "$build_target_arch" ]; then
    platform_triplet=""
  # otherwise, the cargo target path is build with the platform triplet
  else
    platform_triplet="$build_target_arch-pc-windows-msvc"
  fi

  echo "$CARGO_TARGET_DIR/$platform_triplet/${build_mode,,}"
}

# Rust isn't internally consistent w.r.t. architecture names - for build
# targets 32 bit x86 is calles i686, for hosts, it's x86. Hence these need to
# be converted.
function host_arch_to_target_arch
{
  local host_arch=$1

  case $host_arch in
    "x86")
      echo "i686"
      ;;
    "x64")
      echo "x86_64"
      ;;
    *)
      echo $build_target
      ;;
  esac
}

# Since Microsoft likes to name their architectures differently from Rust, this
# function tries to match microsoft names to Rust names.
function arch_from_build_target
{
  local build_target=$1

  case  $build_target in
    "x86")
      echo "i686"
      ;;
    "x64")
      echo "x86_64"
      ;;
    *)
      echo $build_target
      ;;
  esac
}

function rustc_host_arch
{
  rustc.exe --print cfg \
   | grep '^target_arch=' \
   | cut -d'=' -f2 \
   | tr -d '"'
}


function main
{

  local wfp_root_path=${CPP_ROOT_PATH:-"./windows/winfw"}

  build_wfpctl $wfp_root_path
  copy_outputs $wfp_root_path
}

main