Alpha Composition，即本文简单讨论下Alpha通道引入后，每个像素的颜色计算方式。

A: rgba(255,255,255,1)

B: rgba(0,0,0,0.7)

B over A:  result = 255*1 *0.3 + 0 *0.7 = 76

A: rgba(255, 255, 255, 0.5)
B: rgba(100, 100, 100, 0.8)

B over A: result = 255*0.5 * 0.2 + 100 * 0.8 = 105

即： ColorResult = (ColorA * AlphaA) * (1 - AlphaB) + ColorB * AlphaB 

其中`ColorA*AlphaA`可以称为“预乘”，由此我们上述算出来的ColorResult其实也是预乘结果。由此，它可以进一步继续代入后续计算。即

PreMultpleColor(n) = PreMultipleColor(n-1)* (1- alphaN) + ColorN * alphaN。

其实很好理解，就是之前的结果仅可以贡献(1-alphaB)的分量，ColorB的贡献会被alphaB直接相乘。由此，若alphaB为0，则认为完全使用之前的结果，若alphaB为1，则认为完全使用ColorB。

对于xor，即亦或操作，即AlphaA ^ AlphaB ，此时它们取值应该限定于0或1。

## 拓展阅读

- [alpha compositing wiki](https://en.wikipedia.org/wiki/Alpha_compositing)