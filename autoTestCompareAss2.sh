for i in $(seq 1 31)
do
    java VC.vc Recogniser/t$i.vc > MyTestResult/mine_t$i.sol

    diff -c Recogniser/t$i.sol MyTestResult/mine_t$i.sol
    echo
done