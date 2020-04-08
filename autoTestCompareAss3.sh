for i in $(seq 1 47)
do
    java VC.vc Parser/t$i.vc
    java VC.vc -u Parser/t$i.vcuu Parser/t$i.vcu
    diff Parser/t$i.vcu Parser/t$i.vcuu #> Ass3AutoTestResult.txt
    echo
done