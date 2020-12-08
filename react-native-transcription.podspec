require "json"

package = JSON.parse(File.read(File.join(__dir__, "package.json")))

Pod::Spec.new do |s|
  s.name         = "react-native-transcription"
  s.version      = package["version"]
  s.summary      = package["description"]
  s.homepage     = package["homepage"]
  s.license      = package["license"]
  s.authors      = package["author"]

  s.platforms    = { :ios => "13.5" }
  s.source       = { :git => "https://github.com/zaptrem/react-native-transcription.git", :tag => "#{s.version}" }

  
  s.source_files = "ios/*.{h,m,mm,swift}"
  s.vendored_frameworks    = ['ios/Frameworks/deepspeech_ios.framework']
  #s.swift_versions = ['5.1.3']

  s.dependency "React"
end
